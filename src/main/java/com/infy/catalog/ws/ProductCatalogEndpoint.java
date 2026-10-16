package com.infy.catalog.ws;

import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.DuplicateSkuException;
import com.infy.catalog.domain.exception.InvalidRequestException;
import com.infy.catalog.domain.exception.ProductNotFoundException;
import com.infy.catalog.service.ProductService;
import com.infy.catalog.ws.dto.ProductDto;
import com.infy.catalog.ws.dto.ProductInput;
import com.infy.catalog.ws.dto.ProductPage;
import com.infy.catalog.ws.dto.SearchRequest;
import com.infy.catalog.ws.fault.CatalogFaultInfo;
import com.infy.catalog.ws.fault.CatalogServiceException;
import com.infy.catalog.ws.fault.FaultCode;
import com.infy.catalog.ws.handler.CorrelationIdHandler;
import jakarta.jws.WebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thin SOAP adapter: maps XML types to the domain, calls {@link ProductService}, and turns
 * every failure into a typed {@code CatalogFault}. No business logic lives here.
 */
@WebService(
        endpointInterface = "com.infy.catalog.ws.ProductCatalogPort",
        serviceName = "ProductCatalogService",
        portName = "ProductCatalogPort",
        targetNamespace = Namespaces.CATALOG_V1)
public class ProductCatalogEndpoint implements ProductCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogEndpoint.class);

    private final ProductService service;

    public ProductCatalogEndpoint(ProductService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public ProductDto createProduct(ProductInput product) throws CatalogServiceException {
        return call(() -> ProductMapper.toDto(service.create(ProductMapper.toData(product))));
    }

    @Override
    public ProductDto getProduct(long id) throws CatalogServiceException {
        return call(() -> ProductMapper.toDto(service.get(id)));
    }

    @Override
    public ProductDto updateProduct(long id, ProductInput product) throws CatalogServiceException {
        return call(() -> ProductMapper.toDto(service.update(id, ProductMapper.toData(product))));
    }

    @Override
    public void deleteProduct(long id) throws CatalogServiceException {
        call(() -> {
            service.delete(id);
            return null;
        });
    }

    @Override
    public ProductPage listProducts(Integer page, Integer size) throws CatalogServiceException {
        return call(() -> ProductMapper.toPage(
                service.search(ProductSearchCriteria.empty(), page, size, SortField.ID, SortDirection.ASC)));
    }

    @Override
    public ProductPage searchProducts(SearchRequest criteria) throws CatalogServiceException {
        SearchRequest request = criteria == null ? new SearchRequest() : criteria;
        return call(() -> ProductMapper.toPage(service.search(
                ProductMapper.toCriteria(request),
                request.getPage(),
                request.getSize(),
                ProductMapper.toSortField(request.getSortBy()),
                ProductMapper.toDirection(request.getSortOrder()))));
    }

    private static <T> T call(Supplier<T> action) throws CatalogServiceException {
        try {
            return action.get();
        } catch (ProductNotFoundException e) {
            throw fault(FaultCode.PRODUCT_NOT_FOUND, e.getMessage(), List.of());
        } catch (DuplicateSkuException e) {
            throw fault(FaultCode.DUPLICATE_SKU, e.getMessage(), List.of());
        } catch (InvalidRequestException e) {
            throw fault(FaultCode.VALIDATION_FAILED, e.getMessage(), e.violations());
        } catch (RuntimeException e) {
            // Full detail goes to the log (with traceId); the client gets a generic message only.
            log.error("Unexpected error while handling SOAP request", e);
            throw fault(FaultCode.INTERNAL_ERROR,
                    "Unexpected server error. Quote the traceId when reporting this issue.", List.of());
        }
    }

    private static CatalogServiceException fault(FaultCode code, String message, List<String> details) {
        String traceId = Objects.requireNonNullElse(MDC.get(CorrelationIdHandler.MDC_KEY), "n/a");
        if (code != FaultCode.INTERNAL_ERROR) {
            log.info("Request rejected: code={} message=\"{}\" details={}", code, message, details);
        }
        return new CatalogServiceException(message, new CatalogFaultInfo(code, message, details, traceId));
    }
}
