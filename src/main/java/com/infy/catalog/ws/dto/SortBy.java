package com.infy.catalog.ws.dto;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/** Published as an xs:enumeration. Adding a value is backward compatible; removing one is not. */
@XmlType(name = "sortBy")
@XmlEnum
public enum SortBy {
    ID,
    NAME,
    PRICE,
    STOCK,
    CREATED_AT
}
