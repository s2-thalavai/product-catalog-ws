package com.infy.catalog.ws;

import com.infy.catalog.Application;
import com.infy.catalog.client.CatalogClient;
import com.infy.catalog.config.AppConfig;
import com.infy.catalog.infra.SoapRuntimeDefaults;
import com.infy.catalog.ws.dto.ProductDto;
import com.infy.catalog.ws.dto.ProductInput;
import com.infy.catalog.ws.dto.ProductPage;
import com.infy.catalog.ws.dto.SearchRequest;
import com.infy.catalog.ws.dto.SortBy;
import com.infy.catalog.ws.dto.SortOrder;
import com.infy.catalog.ws.fault.CatalogFaultInfo;
import com.infy.catalog.ws.fault.CatalogServiceException;
import com.infy.catalog.ws.fault.FaultCode;
import com.infy.catalog.ws.handler.CorrelationIdHandler;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.handler.MessageContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end over real HTTP: starts the application on a random port (in-memory store)
 * and calls it through a JAX-WS proxy built from the published WSDL.
 * Tests share one server, so each uses unique SKUs and its own category.
 */
class ProductCatalogEndpointIT {

    private static final String PATH = "/ws/v1/products";

    private static Application app;
    private static String baseUrl;
    private static ProductCatalogPort client;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        SoapRuntimeDefaults.configure();
        app = Application.start(AppConfig.fromEnvironment(Map.of(
                "APP_PORT", "0",
                "SHUTDOWN_GRACE_SECONDS", "0",
                "PAGE_SIZE_MAX", "50")));
        baseUrl = "http://localhost:" + app.port();
        client = CatalogClient.connect(baseUrl + PATH);
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void createGetUpdateDeleteRoundTrip() throws Exception {
        String sku = uniqueSku();

        ProductDto created = client.createProduct(input(sku, "Round Trip Lamp", "it-crud", "1299.50", 10));
        assertNotNull(created.getId());
        assertEquals(sku, created.getSku());
        assertEquals(0, new BigDecimal("1299.50").compareTo(created.getPrice()));
        assertNotNull(created.getCreatedAt());

        ProductDto fetched = client.getProduct(created.getId());
        assertEquals("Round Trip Lamp", fetched.getName());

        ProductDto updated = client.updateProduct(created.getId(), input(sku, "Round Trip Lamp v2", "it-crud", "999.00", 4));
        assertEquals("Round Trip Lamp v2", updated.getName());
        assertEquals(4, updated.getStock());
        assertEquals(created.getCreatedAt(), updated.getCreatedAt());

        client.deleteProduct(created.getId());
        CatalogServiceException gone = assertThrows(CatalogServiceException.class, () -> client.getProduct(created.getId()));
        assertEquals(FaultCode.PRODUCT_NOT_FOUND, gone.getFaultInfo().getCode());
    }

    @Test
    void unknownProductReturnsTypedFaultWithTraceId() {
        CatalogServiceException e = assertThrows(CatalogServiceException.class, () -> client.getProduct(987_654_321L));

        CatalogFaultInfo fault = e.getFaultInfo();
        assertEquals(FaultCode.PRODUCT_NOT_FOUND, fault.getCode());
        assertTrue(fault.getMessage().contains("987654321"));
        assertFalse(fault.getTraceId().isBlank());
    }

    @Test
    void invalidProductReturnsEveryViolation() {
        ProductInput invalid = new ProductInput("x", "", null, null, new BigDecimal("-1"), -5);

        CatalogServiceException e = assertThrows(CatalogServiceException.class, () -> client.createProduct(invalid));

        CatalogFaultInfo fault = e.getFaultInfo();
        assertEquals(FaultCode.VALIDATION_FAILED, fault.getCode());
        assertEquals(4, fault.getDetails().size(), fault.getDetails()::toString);
        assertTrue(fault.getDetails().contains("name is required"));
    }

    @Test
    void duplicateSkuIsRejected() throws Exception {
        String sku = uniqueSku();
        client.createProduct(input(sku, "First", "it-dup", "10.00", 1));

        CatalogServiceException e = assertThrows(CatalogServiceException.class,
                () -> client.createProduct(input(sku, "Second", "it-dup", "10.00", 1)));

        assertEquals(FaultCode.DUPLICATE_SKU, e.getFaultInfo().getCode());
    }

    @Test
    void searchFiltersSortsAndPaginates() throws Exception {
        String category = "it-search-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 7; i++) {
            int stock = i % 3 == 0 ? 0 : 5; // items 3 and 6 are out of stock
            client.createProduct(input(uniqueSku(), "Search Item " + i, category, String.valueOf(100 * i), stock));
        }
        SearchRequest request = new SearchRequest();
        request.setCategory(category);
        request.setMinPrice(new BigDecimal("200"));
        request.setInStockOnly(true);
        request.setSortBy(SortBy.PRICE);
        request.setSortOrder(SortOrder.DESC);
        request.setSize(2);

        request.setPage(0);
        ProductPage first = client.searchProducts(request);
        request.setPage(1);
        ProductPage second = client.searchProducts(request);

        // matches: 700, 500, 400, 200
        assertEquals(4, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(List.of("Search Item 7", "Search Item 5"), names(first));
        assertTrue(first.hasNext());
        assertFalse(first.hasPrevious());
        assertEquals(List.of("Search Item 4", "Search Item 2"), names(second));
        assertFalse(second.hasNext());
        assertTrue(second.hasPrevious());
    }

    @Test
    void pagingDefaultsAndCapAreApplied() throws Exception {
        assertEquals(20, client.listProducts(null, null).getSize());

        SearchRequest huge = new SearchRequest();
        huge.setSize(5_000);
        assertEquals(50, client.searchProducts(huge).getSize());
    }

    @Test
    void invalidSearchReturnsValidationFault() {
        SearchRequest request = new SearchRequest();
        request.setMinPrice(new BigDecimal("500"));
        request.setMaxPrice(new BigDecimal("100"));

        CatalogServiceException e = assertThrows(CatalogServiceException.class, () -> client.searchProducts(request));

        assertEquals(FaultCode.VALIDATION_FAILED, e.getFaultInfo().getCode());
        assertEquals(List.of("minPrice must be <= maxPrice"), e.getFaultInfo().getDetails());
    }

    @Test
    void correlationIdIsEchoedInFaultsAndResponseHeader() throws Exception {
        ProductCatalogPort traced = CatalogClient.connect(baseUrl + PATH, "it-trace-123");

        CatalogServiceException e = assertThrows(CatalogServiceException.class, () -> traced.getProduct(1_000_000L));
        assertEquals("it-trace-123", e.getFaultInfo().getTraceId());

        traced.listProducts(0, 1);
        Object headers = ((BindingProvider) traced).getResponseContext().get(MessageContext.HTTP_RESPONSE_HEADERS);
        assertEquals("it-trace-123", header(headers, CorrelationIdHandler.HEADER));
    }

    @Test
    void healthEndpointsReportUp() throws Exception {
        assertEquals(200, get("/health/live").statusCode());
        HttpResponse<String> ready = get("/health/ready");
        assertEquals(200, ready.statusCode());
        assertTrue(ready.body().contains("UP"));
    }

    @Test
    void wsdlIsPublished() throws Exception {
        HttpResponse<String> wsdl = get(PATH + "?wsdl");

        assertEquals(200, wsdl.statusCode());
        assertTrue(wsdl.body().contains("ProductCatalogService"));
        assertTrue(wsdl.body().contains("searchProducts"));
    }

    @Test
    void faultsNeverExposeStackTraces() throws Exception {
        String envelope = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:cat="http://catalog.infy.com/v1">
                  <soapenv:Body><cat:getProduct><id>424242</id></cat:getProduct></soapenv:Body>
                </soapenv:Envelope>""";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertTrue(response.body().contains("PRODUCT_NOT_FOUND"), response.body());
        assertFalse(response.body().contains("stackTrace"), response.body());
        assertFalse(response.body().contains("at com.infy"), response.body());
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Header names may come back in any case (the JDK server normalizes them). */
    private static String header(Object headers, String name) {
        if (headers instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && key.equalsIgnoreCase(name)
                        && entry.getValue() instanceof List<?> values && !values.isEmpty()) {
                    return String.valueOf(values.get(0));
                }
            }
        }
        return null;
    }

    private static List<String> names(ProductPage page) {
        return page.getItems().stream().map(ProductDto::getName).toList();
    }

    private static ProductInput input(String sku, String name, String category, String price, int stock) {
        return new ProductInput(sku, name, null, category, new BigDecimal(price), stock);
    }

    private static String uniqueSku() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
