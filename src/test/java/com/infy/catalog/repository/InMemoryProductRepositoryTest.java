package com.infy.catalog.repository;

class InMemoryProductRepositoryTest extends ProductRepositoryContract {

    @Override
    protected ProductRepository createRepository() {
        return new InMemoryProductRepository();
    }
}
