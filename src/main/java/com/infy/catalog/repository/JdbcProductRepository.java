package com.infy.catalog.repository;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.PageRequest;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.exception.DuplicateSkuException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL store using plain JDBC. Every value is a bind parameter; the only SQL built
 * from input is ORDER BY, and that comes from the {@link com.infy.catalog.domain.SortField}
 * whitelist.
 */
public class JdbcProductRepository implements ProductRepository {

    private static final String COLUMNS =
            "id, sku, name, description, category, price, stock, created_at, updated_at";
    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public JdbcProductRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Product insert(ProductData data) {
        String sql = "INSERT INTO product (sku, name, description, category, price, stock) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindData(statement, data);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return map(rs);
            }
        } catch (SQLException e) {
            throw translate(e, data.sku());
        }
    }

    @Override
    public Optional<Product> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM product WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw translate(e, null);
        }
    }

    @Override
    public Optional<Product> update(long id, ProductData data) {
        String sql = "UPDATE product SET sku = ?, name = ?, description = ?, category = ?, price = ?, stock = ?, "
                + "updated_at = now() WHERE id = ? RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindData(statement, data);
            statement.setLong(7, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw translate(e, data.sku());
        }
    }

    @Override
    public boolean deleteById(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM product WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate(e, null);
        }
    }

    @Override
    public Page<Product> search(ProductSearchCriteria criteria, PageRequest pageRequest) {
        List<Object> params = new ArrayList<>();
        String where = whereClause(criteria, params);

        try (Connection connection = dataSource.getConnection()) {
            long total;
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM product" + where)) {
                bindAll(count, params);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            if (total == 0 || pageRequest.offset() >= total) {
                return new Page<>(List.of(), pageRequest.page(), pageRequest.size(), total);
            }

            String sql = "SELECT " + COLUMNS + " FROM product" + where
                    + " ORDER BY " + orderBy(pageRequest) + " LIMIT ? OFFSET ?";
            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(pageRequest.size());
            pageParams.add(pageRequest.offset());

            List<Product> items = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(sql)) {
                bindAll(query, pageParams);
                try (ResultSet rs = query.executeQuery()) {
                    while (rs.next()) {
                        items.add(map(rs));
                    }
                }
            }
            return new Page<>(items, pageRequest.page(), pageRequest.size(), total);
        } catch (SQLException e) {
            throw translate(e, null);
        }
    }

    private static String whereClause(ProductSearchCriteria criteria, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (criteria.keyword() != null) {
            String pattern = "%" + escapeLike(criteria.keyword().toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(name) LIKE ? ESCAPE '\\'")
                 .append(" OR LOWER(sku) LIKE ? ESCAPE '\\'")
                 .append(" OR LOWER(COALESCE(description, '')) LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (criteria.category() != null) {
            where.append(" AND LOWER(category) = ?");
            params.add(criteria.category().toLowerCase(Locale.ROOT));
        }
        if (criteria.minPrice() != null) {
            where.append(" AND price >= ?");
            params.add(criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            where.append(" AND price <= ?");
            params.add(criteria.maxPrice());
        }
        if (criteria.inStockOnly()) {
            where.append(" AND stock > 0");
        }
        return where.toString();
    }

    private static String orderBy(PageRequest request) {
        String column = switch (request.sort()) {
            case ID -> "id";
            case NAME -> "LOWER(name)";
            case PRICE -> "price";
            case STOCK -> "stock";
            case CREATED_AT -> "created_at";
        };
        String direction = request.direction() == SortDirection.DESC ? "DESC" : "ASC";
        return column + " " + direction + ", id ASC";
    }

    /** Makes % and _ in user input match literally. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static void bindData(PreparedStatement statement, ProductData data) throws SQLException {
        statement.setString(1, data.sku());
        statement.setString(2, data.name());
        statement.setString(3, data.description());
        statement.setString(4, data.category());
        statement.setBigDecimal(5, data.price());
        statement.setInt(6, data.stock());
    }

    private static void bindAll(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }

    private static Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static RuntimeException translate(SQLException e, String sku) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState()) && sku != null) {
            return new DuplicateSkuException(sku);
        }
        return new DataAccessException("Database operation failed (SQLState " + e.getSQLState() + ")", e);
    }
}
