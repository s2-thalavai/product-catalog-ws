package com.infy.catalog.client;

import com.infy.catalog.infra.SoapRuntimeDefaults;
import com.infy.catalog.ws.Namespaces;
import com.infy.catalog.ws.ProductCatalogPort;
import com.infy.catalog.ws.dto.ProductDto;
import com.infy.catalog.ws.dto.ProductInput;
import com.infy.catalog.ws.dto.ProductPage;
import com.infy.catalog.ws.dto.SearchRequest;
import com.infy.catalog.ws.dto.SortBy;
import com.infy.catalog.ws.dto.SortOrder;
import com.infy.catalog.ws.fault.CatalogFaultInfo;
import com.infy.catalog.ws.fault.CatalogServiceException;
import com.infy.catalog.ws.handler.CorrelationIdHandler;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.handler.MessageContext;

import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proxy-based consumer (steps 4-6 of the SOAP flow: fetch WSDL, create proxy, call methods).
 * Also a runnable demo:
 * <pre>
 *   java -cp target/product-catalog-ws.jar com.infy.catalog.client.CatalogClient http://localhost:8080/ws/v1/products
 * </pre>
 * A separate consumer project would generate these classes from the WSDL with wsimport
 * (jaxws-maven-plugin) instead of sharing the server's interface.
 */
public final class CatalogClient {

    public static final QName SERVICE_NAME = new QName(Namespaces.CATALOG_V1, "ProductCatalogService");

    private static final String CONNECT_TIMEOUT = "com.sun.xml.ws.connect.timeout";
    private static final String REQUEST_TIMEOUT = "com.sun.xml.ws.request.timeout";

    private CatalogClient() {
    }

    public static ProductCatalogPort connect(String endpointUrl) {
        return connect(endpointUrl, null);
    }

    /**
     * @param endpointUrl   e.g. http://localhost:8080/ws/v1/products
     * @param correlationId optional X-Correlation-Id sent with every call
     */
    public static ProductCatalogPort connect(String endpointUrl, String correlationId) {
        try {
            Service service = Service.create(URI.create(endpointUrl + "?wsdl").toURL(), SERVICE_NAME);
            ProductCatalogPort port = service.getPort(ProductCatalogPort.class);
            Map<String, Object> request = ((BindingProvider) port).getRequestContext();
            // Call the URL we were given, not the address inside the WSDL (differs behind ingress/port-forward).
            request.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointUrl);
            request.put(CONNECT_TIMEOUT, 5_000);
            request.put(REQUEST_TIMEOUT, 15_000);
            if (correlationId != null) {
                Map<String, List<String>> headers = new HashMap<>();
                headers.put(CorrelationIdHandler.HEADER, List.of(correlationId));
                request.put(MessageContext.HTTP_REQUEST_HEADERS, headers);
            }
            return port;
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid endpoint URL: " + endpointUrl, e);
        }
    }

    public static void main(String[] args) throws Exception {
        SoapRuntimeDefaults.configure();
        String url = args.length > 0 ? args[0] : "http://localhost:8080/ws/v1/products";
        String correlationId = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        ProductCatalogPort catalog = connect(url, correlationId);
        System.out.println("Endpoint: " + url + "   X-Correlation-Id: " + correlationId);

        System.out.println("\n== Electronics, cheapest first, 3 per page ==");
        SearchRequest search = new SearchRequest();
        search.setCategory("Electronics");
        search.setSortBy(SortBy.PRICE);
        search.setSortOrder(SortOrder.ASC);
        search.setSize(3);
        int page = 0;
        ProductPage result;
        do {
            search.setPage(page);
            result = catalog.searchProducts(search);
            System.out.printf("-- page %d of %d (%d matches)%n", page + 1, result.getTotalPages(), result.getTotalElements());
            result.getItems().forEach(p -> System.out.println("   " + p));
            page++;
        } while (result.hasNext());

        System.out.println("\n== CRUD ==");
        String sku = "DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ProductDto created = catalog.createProduct(
                new ProductInput(sku, "Demo Desk Organizer", "Bamboo organizer", "Office", new BigDecimal("899.00"), 40));
        System.out.println("created: " + created);
        System.out.println("read:    " + catalog.getProduct(created.getId()));
        ProductDto updated = catalog.updateProduct(created.getId(),
                new ProductInput(sku, "Demo Desk Organizer", "Bamboo organizer", "Office", new BigDecimal("799.00"), 35));
        System.out.println("updated: " + updated);
        catalog.deleteProduct(created.getId());
        System.out.println("deleted: " + created.getId());

        System.out.println("\n== Faults ==");
        try {
            catalog.getProduct(created.getId());
        } catch (CatalogServiceException e) {
            print(e.getFaultInfo());
        }
        try {
            catalog.createProduct(new ProductInput("x", "", null, null, new BigDecimal("-1"), -5));
        } catch (CatalogServiceException e) {
            print(e.getFaultInfo());
        }
    }

    private static void print(CatalogFaultInfo fault) {
        System.out.printf("   %s: %s %s (traceId=%s)%n", fault.getCode(), fault.getMessage(), fault.getDetails(), fault.getTraceId());
    }
}
