package com.infy.catalog.ws;

import com.infy.catalog.ws.dto.ProductDto;
import com.infy.catalog.ws.dto.ProductInput;
import com.infy.catalog.ws.dto.ProductPage;
import com.infy.catalog.ws.dto.SearchRequest;
import com.infy.catalog.ws.fault.CatalogServiceException;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.bind.annotation.XmlElement;

/**
 * Service Endpoint Interface = the WSDL portType "ProductCatalog".
 * Document/literal wrapped (the JAX-WS default). Everything is HTTP POST to one URL;
 * the operation name says what to do.
 *
 * <pre>
 *   SOAP operation   REST equivalent (later in the course)
 *   createProduct    POST   /products
 *   getProduct       GET    /products/{id}
 *   updateProduct    PUT    /products/{id}
 *   deleteProduct    DELETE /products/{id}
 *   listProducts     GET    /products?page=&size=
 *   searchProducts   GET    /products?keyword=&category=&minPrice=&maxPrice=&inStockOnly=&sort=
 * </pre>
 *
 * Contract rule: changes must be additive (new optional elements, new operations).
 * WsdlContractIT fails the build when the published WSDL/XSD changes.
 */
@WebService(name = "ProductCatalog", targetNamespace = Namespaces.CATALOG_V1)
public interface ProductCatalogPort {

    @WebMethod(operationName = "createProduct")
    @WebResult(name = "product")
    ProductDto createProduct(
            @WebParam(name = "product") @XmlElement(required = true) ProductInput product)
            throws CatalogServiceException;

    @WebMethod(operationName = "getProduct")
    @WebResult(name = "product")
    ProductDto getProduct(@WebParam(name = "id") long id) throws CatalogServiceException;

    @WebMethod(operationName = "updateProduct")
    @WebResult(name = "product")
    ProductDto updateProduct(
            @WebParam(name = "id") long id,
            @WebParam(name = "product") @XmlElement(required = true) ProductInput product)
            throws CatalogServiceException;

    @WebMethod(operationName = "deleteProduct")
    void deleteProduct(@WebParam(name = "id") long id) throws CatalogServiceException;

    /** Products sorted by id. page is 0-based; size defaults to PAGE_SIZE_DEFAULT, capped at PAGE_SIZE_MAX. */
    @WebMethod(operationName = "listProducts")
    @WebResult(name = "result")
    ProductPage listProducts(
            @WebParam(name = "page") Integer page,
            @WebParam(name = "size") Integer size) throws CatalogServiceException;

    /** Filtered, sorted, paginated search. Every criterion is optional. */
    @WebMethod(operationName = "searchProducts")
    @WebResult(name = "result")
    ProductPage searchProducts(@WebParam(name = "criteria") SearchRequest criteria) throws CatalogServiceException;
}
