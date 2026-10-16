package com.infy.catalog;

import com.infy.catalog.config.AppConfig;
import com.infy.catalog.config.AppConfig.StoreType;
import com.infy.catalog.infra.DataSourceFactory;
import com.infy.catalog.infra.DatabaseMigrations;
import com.infy.catalog.infra.HealthHttpHandler;
import com.infy.catalog.infra.SoapRuntimeDefaults;
import com.infy.catalog.repository.InMemoryProductRepository;
import com.infy.catalog.repository.JdbcProductRepository;
import com.infy.catalog.repository.ProductRepository;
import com.infy.catalog.repository.SeedData;
import com.infy.catalog.service.PagingPolicy;
import com.infy.catalog.service.ProductService;
import com.infy.catalog.ws.ProductCatalogEndpoint;
import com.infy.catalog.ws.handler.CorrelationIdHandler;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Composition root: wires config, storage, service and the SOAP endpoint, and serves them on
 * the JDK's built-in HTTP server (no servlet container). Owns graceful shutdown.
 *
 * <pre>
 *   POST /ws/v1/products          SOAP endpoint
 *   GET  /ws/v1/products?wsdl     WSDL
 *   GET  /health/live, /health/ready
 * </pre>
 */
public final class Application implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private final AppConfig config;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final HttpServer server;
    private final Endpoint endpoint;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public static void main(String[] args) {
        SoapRuntimeDefaults.configure();
        try {
            AppConfig config = AppConfig.fromEnvironment(System.getenv());
            log.info("Starting Product Catalog SOAP service with {}", config);
            Application app = start(config);
            Runtime.getRuntime().addShutdownHook(new Thread(app::close, "graceful-shutdown"));
        } catch (RuntimeException e) {
            log.error("Startup failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /** Starts the service. Port 0 picks a free port (used by tests); see {@link #port()}. */
    public static Application start(AppConfig config) {
        HikariDataSource dataSource = null;
        try {
            ProductRepository repository;
            if (config.storeType() == StoreType.POSTGRES) {
                dataSource = DataSourceFactory.create(config);
                DatabaseMigrations.migrate(dataSource, config.seedData());
                repository = new JdbcProductRepository(dataSource);
            } else {
                log.warn("STORE_TYPE=memory: data is lost on restart and not shared between replicas");
                repository = new InMemoryProductRepository();
                if (config.seedData()) {
                    SeedData.PRODUCTS.forEach(repository::insert);
                }
            }
            ProductService service = new ProductService(repository,
                    new PagingPolicy(config.defaultPageSize(), config.maxPageSize()));
            return new Application(config, service, dataSource);
        } catch (IOException e) {
            closeQuietly(dataSource);
            throw new UncheckedIOException("Could not start HTTP server on port " + config.port(), e);
        } catch (RuntimeException e) {
            closeQuietly(dataSource);
            throw e;
        }
    }

    private Application(AppConfig config, ProductService service, HikariDataSource dataSource) throws IOException {
        this.config = config;
        this.dataSource = dataSource;
        this.executor = Executors.newFixedThreadPool(config.httpThreads(), namedThreads("http-worker-"));
        try {
            this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
            server.setExecutor(executor);
            server.createContext("/health", new HealthHttpHandler(this::isReady));

            this.endpoint = Endpoint.create(new ProductCatalogEndpoint(service));
            @SuppressWarnings("rawtypes")
            List<Handler> handlerChain = new ArrayList<>();
            handlerChain.add(new CorrelationIdHandler());
            endpoint.getBinding().setHandlerChain(handlerChain);
            endpoint.publish(server.createContext(config.contextPath()));

            server.start();
        } catch (IOException | RuntimeException e) {
            executor.shutdownNow();
            throw e;
        }
        ready.set(true);
        log.info("Listening on port {} - WSDL: http://localhost:{}{}?wsdl", port(), port(), config.contextPath());
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public boolean isReady() {
        return ready.get() && databaseAnswers();
    }

    /**
     * Graceful shutdown (SIGTERM from Kubernetes): report not-ready, stop accepting connections,
     * let in-flight requests finish for up to SHUTDOWN_GRACE_SECONDS, then release resources.
     */
    @Override
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        log.info("Shutting down, waiting up to {}s for in-flight requests", config.shutdownGraceSeconds());
        ready.set(false);
        server.stop(config.shutdownGraceSeconds());
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            endpoint.stop();
        } catch (RuntimeException e) {
            log.debug("Endpoint already stopped", e);
        }
        closeQuietly(dataSource);
        log.info("Shutdown complete");
    }

    private boolean databaseAnswers() {
        if (dataSource == null) {
            return true;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            log.warn("Readiness check: database unavailable ({})", e.getMessage());
            return false;
        }
    }

    private static void closeQuietly(HikariDataSource dataSource) {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> new Thread(runnable, prefix + counter.incrementAndGet());
    }
}
