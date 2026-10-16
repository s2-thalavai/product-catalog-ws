package com.infy.catalog.ws.dto;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** JAXB has no built-in java.time support. Writes UTC ISO-8601 (xs:dateTime), e.g. 2026-10-16T02:21:00Z. */
public class InstantXmlAdapter extends XmlAdapter<String, Instant> {

    @Override
    public Instant unmarshal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException e) {
            // xs:dateTime without a zone: treat as UTC
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
        }
    }

    @Override
    public String marshal(Instant value) {
        return value == null ? null : value.toString();
    }
}
