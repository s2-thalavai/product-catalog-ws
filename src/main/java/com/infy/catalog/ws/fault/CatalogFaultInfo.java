package com.infy.catalog.ws.fault;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fault detail sent inside {@code <soap:Fault><detail><CatalogFault>}. Contains a code, a
 * human message, per-field details and the traceId; never stack traces or SQL.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "catalogFaultInfo", propOrder = {"code", "message", "details", "traceId"})
public class CatalogFaultInfo {

    @XmlElement(required = true)
    private FaultCode code;

    @XmlElement(required = true)
    private String message;

    @XmlElementWrapper(name = "details")
    @XmlElement(name = "detail")
    private List<String> details = new ArrayList<>();

    @XmlElement(required = true)
    private String traceId;

    public CatalogFaultInfo() {
        // required by JAXB
    }

    public CatalogFaultInfo(FaultCode code, String message, List<String> details, String traceId) {
        this.code = code;
        this.message = message;
        this.details = new ArrayList<>(details);
        this.traceId = traceId;
    }

    public FaultCode getCode() { return code; }
    public String getMessage() { return message; }
    public List<String> getDetails() { return details; }
    public String getTraceId() { return traceId; }
}
