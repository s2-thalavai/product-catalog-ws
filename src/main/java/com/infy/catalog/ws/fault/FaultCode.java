package com.infy.catalog.ws.fault;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Machine-readable error codes. Roughly the REST equivalents:
 * PRODUCT_NOT_FOUND=404, DUPLICATE_SKU=409, VALIDATION_FAILED=400/422, INTERNAL_ERROR=500.
 */
@XmlType(name = "faultCode")
@XmlEnum
public enum FaultCode {
    PRODUCT_NOT_FOUND,
    DUPLICATE_SKU,
    VALIDATION_FAILED,
    INTERNAL_ERROR
}
