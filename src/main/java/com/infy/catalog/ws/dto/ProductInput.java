package com.infy.catalog.ws.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

/** Request body for createProduct / updateProduct. The id is never taken from the body. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "productInput", propOrder = {"sku", "name", "description", "category", "price", "stock"})
public class ProductInput {

    @XmlElement(required = true)
    private String sku;

    @XmlElement(required = true)
    private String name;

    private String description;

    private String category;

    @XmlElement(required = true)
    private BigDecimal price;

    @XmlElement(required = true)
    private Integer stock;

    public ProductInput() {
        // required by JAXB
    }

    public ProductInput(String sku, String name, String description, String category,
                        BigDecimal price, Integer stock) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
