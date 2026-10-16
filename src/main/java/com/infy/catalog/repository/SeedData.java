package com.infy.catalog.repository;

import com.infy.catalog.domain.ProductData;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demo catalog for the in-memory store (SEED_DATA=true). Keep in sync with
 * src/main/resources/db/seed/V100__seed_products.sql, which seeds PostgreSQL.
 */
public final class SeedData {

    public static final List<ProductData> PRODUCTS = List.of(
            p("ELEC-MOUSE-01", "Wireless Mouse", "2.4 GHz wireless optical mouse", "Electronics", "799.00", 120),
            p("ELEC-KEYB-01", "Mechanical Keyboard", "Tenkeyless mechanical keyboard with brown switches", "Electronics", "3499.00", 45),
            p("ELEC-HUB-01", "USB-C Hub", "7-in-1 USB-C hub with HDMI and card reader", "Electronics", "1899.00", 60),
            p("ELEC-HEAD-01", "Noise Cancelling Headphones", "Over-ear Bluetooth headphones with ANC", "Electronics", "8999.00", 25),
            p("ELEC-CHRG-01", "Fast Charger 65W", "GaN USB-C fast charger", "Electronics", "1499.00", 0),
            p("ELEC-WCAM-01", "Full HD Webcam", "1080p webcam with dual microphones", "Electronics", "2299.00", 35),
            p("ELEC-SSD-01", "Portable SSD 1TB", "USB 3.2 portable solid state drive", "Electronics", "6499.00", 18),
            p("HOME-BOTL-01", "Steel Water Bottle", "1 litre insulated stainless steel bottle", "Home", "549.00", 300),
            p("HOME-LAMP-01", "LED Desk Lamp", "Dimmable LED lamp with USB charging port", "Home", "1299.00", 75),
            p("HOME-MUG-01", "Ceramic Coffee Mug", "350 ml ceramic mug", "Home", "249.00", 500),
            p("HOME-PILW-01", "Memory Foam Pillow", "Contour memory foam pillow", "Home", "1599.00", 40),
            p("HOME-CKWR-01", "Non-stick Cookware Set", "3-piece non-stick cookware set", "Home", "2799.00", 0),
            p("FIT-YMAT-01", "Yoga Mat", "6 mm anti-slip yoga mat", "Fitness", "999.00", 80),
            p("FIT-DMBL-01", "Adjustable Dumbbells", "Pair of adjustable dumbbells up to 10 kg", "Fitness", "4599.00", 12),
            p("FIT-RBND-01", "Resistance Bands Set", "Set of 5 latex resistance bands", "Fitness", "699.00", 150),
            p("FIT-SBTL-01", "Shaker Bottle", "700 ml protein shaker bottle", "Fitness", "299.00", 220),
            p("FIT-JROP-01", "Skipping Rope", "Speed skipping rope with ball bearings", "Fitness", "399.00", 0),
            p("BOOK-JAVA-01", "Java Design Patterns Handbook", "Practical design patterns for Java developers", "Books", "1499.00", 30),
            p("BOOK-K8S-01", "Kubernetes in Practice", "Hands-on guide to running Kubernetes", "Books", "2499.00", 22),
            p("BOOK-SQL-01", "SQL for Developers", "Practical SQL for application developers", "Books", "1199.00", 64),
            p("OFFC-NBK-01", "A5 Notebook", "Dotted A5 notebook with 192 pages", "Office", "199.00", 400),
            p("OFFC-PEN-01", "Gel Pen Pack", "Pack of 10 black gel pens", "Office", "149.00", 650),
            p("OFFC-CHR-01", "Ergonomic Office Chair", "Mesh chair with lumbar support", "Office", "12999.00", 8),
            p("OFFC-STND-01", "Laptop Stand", "Adjustable aluminium laptop stand", "Office", "1799.00", 55),
            p("OFFC-WBRD-01", "Whiteboard 60x90", "Magnetic whiteboard with marker tray", "Office", "2199.00", 14));

    private SeedData() {
    }

    private static ProductData p(String sku, String name, String description, String category,
                                 String price, int stock) {
        return new ProductData(sku, name, description, category, new BigDecimal(price), stock);
    }
}
