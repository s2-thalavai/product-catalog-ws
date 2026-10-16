# Product Catalog - SOAP Web Service (JAX-WS)

CRUD, search and pagination for a product catalog, exposed as a **SOAP web service** with
Jakarta XML Web Services (JAX-WS 4 / Eclipse Metro), backed by PostgreSQL, packaged with
Docker, deployed to Kubernetes (k3s/k3d) by a Jenkins pipeline.

```
             ┌──────────── Kubernetes namespace "catalog" ─────────────┐
 SOAP/HTTP   │  Ingress ──► Service ──► Deployment (2-5 pods, HPA)     │
 ──────────► │                            │  JAX-WS endpoint          │
             │                            ▼                           │
             │                  StatefulSet PostgreSQL 16 (k3s)       │
             │                  / managed PostgreSQL (prod)           │
             └────────────────────────────────────────────────────────┘
```

| Operation | Does |
|---|---|
| `createProduct` / `getProduct` / `updateProduct` / `deleteProduct` | CRUD |
| `listProducts(page, size)` | Paginated list |
| `searchProducts(criteria)` | Keyword, category, price range, in-stock filters; sort by id/name/price/stock/createdAt; paginated |

WSDL: `http://localhost:8080/ws/v1/products?wsdl` · faults carry a typed `code` and a `traceId`.

**Quick start**
```bash
mvn -B -ntp clean package -DskipTests
STORE_TYPE=memory SEED_DATA=true java -jar target/product-catalog-ws.jar
./soap-samples/send.sh soap-samples/06-search-filter-sort-page.xml
```

- **[HOW_TO_RUN.md](HOW_TO_RUN.md)**: local run, Docker Compose, tests, k3d/k3s, Jenkins, production
- **[CLAUDE.md](CLAUDE.md)**: architecture, conventions and contract rules for contributors and Claude Code

## Layout
```
src/main/java/com/infy/catalog/
  ws/          SEI, endpoint, JAXB DTOs, faults, correlation-id handler
  service/     business rules, validation, paging policy
  repository/  in-memory + JDBC (PostgreSQL) implementations
  domain/      records and domain exceptions
  infra/       health probes, Hikari, Flyway, Metro settings
  config/      typed env-var configuration
  client/      JAX-WS proxy client + demo
src/main/resources/db/   Flyway migrations (schema) and seed data
k8s/base, k8s/overlays/{k3s,prod}   Kustomize manifests
infra/k3d, infra/k3s, infra/jenkins Cluster and CI setup
soap-samples/  Ready-made SOAP envelopes      scripts/  smoke test, contract baseline
Dockerfile · docker-compose.yml · Jenkinsfile · Makefile
```

## Stack
Java 21 · Metro 4.0 (JAX-WS) · JAXB · HikariCP · PostgreSQL 16 · Flyway · SLF4J/Logback ·
JUnit 5 · Testcontainers · JaCoCo · Docker · Kustomize · k3s/k3d · Traefik · Jenkins (JCasC) · Trivy
