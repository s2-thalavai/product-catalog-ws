package com.infy.catalog.ws.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of products:
 * <pre>
 * &lt;result&gt;
 *   &lt;items&gt;&lt;product&gt;...&lt;/product&gt;&lt;/items&gt;
 *   &lt;page&gt;0&lt;/page&gt;&lt;size&gt;20&lt;/size&gt;&lt;totalElements&gt;25&lt;/totalElements&gt;
 *   &lt;totalPages&gt;2&lt;/totalPages&gt;&lt;hasNext&gt;true&lt;/hasNext&gt;&lt;hasPrevious&gt;false&lt;/hasPrevious&gt;
 * &lt;/result&gt;
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "productPage", propOrder = {
        "items", "page", "size", "totalElements", "totalPages", "hasNext", "hasPrevious"})
public class ProductPage {

    @XmlElementWrapper(name = "items", required = true)
    @XmlElement(name = "product")
    private List<ProductDto> items = new ArrayList<>();

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public ProductPage() {
        // required by JAXB
    }

    public ProductPage(List<ProductDto> items, int page, int size, long totalElements,
                       int totalPages, boolean hasNext, boolean hasPrevious) {
        this.items = new ArrayList<>(items);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public List<ProductDto> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public boolean hasNext() { return hasNext; }
    public boolean hasPrevious() { return hasPrevious; }
}
