package com.infy.catalog.ws.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.math.BigDecimal;
import java.time.Instant;

/** A product as returned to clients. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "product", propOrder = {
        "id", "sku", "name", "description", "category", "price", "stock", "createdAt", "updatedAt"})
public class ProductDto {

    @XmlElement(required = true)
    private Long id;

    @XmlElement(required = true)
    private String sku;

    @XmlElement(required = true)
    private String name;

    private String description;

    private String category;

    @XmlElement(required = true)
    private BigDecimal price;

    private int stock;

    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant createdAt;

    @XmlElement(required = true)
    @XmlSchemaType(name = "dateTime")
    @XmlJavaTypeAdapter(InstantXmlAdapter.class)
    private Instant updatedAt;

    public ProductDto() {
        // required by JAXB
    }

    public ProductDto(Long id, String sku, String name, String description, String category,
                      BigDecimal price, int stock, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", sku=" + sku + ", name='" + name + "', category=" + category
                + ", price=" + price + ", stock=" + stock + "}";
    }
}
