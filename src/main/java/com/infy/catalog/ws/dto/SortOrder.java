package com.infy.catalog.ws.dto;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "sortOrder")
@XmlEnum
public enum SortOrder {
    ASC,
    DESC
}
