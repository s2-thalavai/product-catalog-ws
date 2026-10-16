package com.infy.catalog.ws.fault;

import com.infy.catalog.ws.Namespaces;
import jakarta.xml.ws.WebFault;

/**
 * The single checked fault declared by every operation (wsdl:fault "CatalogFault").
 * Follows the JAX-WS fault-bean pattern: the (String, bean) constructors plus
 * getFaultInfo() let Metro rebuild this exception on the client side.
 */
@WebFault(name = "CatalogFault", targetNamespace = Namespaces.CATALOG_V1)
public class CatalogServiceException extends Exception {

    private final transient CatalogFaultInfo faultInfo;

    public CatalogServiceException(String message, CatalogFaultInfo faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    public CatalogServiceException(String message, CatalogFaultInfo faultInfo, Throwable cause) {
        super(message, cause);
        this.faultInfo = faultInfo;
    }

    public CatalogFaultInfo getFaultInfo() {
        return faultInfo;
    }
}
