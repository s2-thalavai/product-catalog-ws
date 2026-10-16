# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## What this is

A **SOAP web service** (JAX-WS / Jakarta XML Web Services 4, Eclipse Metro) that does CRUD,
search and pagination on a product catalog. It runs on the JDK's built-in HTTP server (no
servlet container), stores data in PostgreSQL (or in memory for local runs), and ships as a
Docker image deployed to Kubernetes (k3s/k3d locally) by a Jenkins pipeline.

It's a learning project for the Spring REST course: the same domain will later be exposed as
REST, so the domain and service layers deliberately know nothing about SOAP.

## Commands

| Task | Command |
|---|---|
| Build jar (no tests) | `mvn -B -ntp clean package -DskipTests` |
| All tests + coverage gate | `mvn -B -ntp clean verify` (ITs need Docker for Testcontainers; skipped without it) |
| Unit tests only | `mvn -B -ntp test` |
| One test | `mvn -Dtest=ProductServiceTest test` / `mvn -Dit.test=ProductCatalogEndpointIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false verify` |
| Run locally | `STORE_TYPE=memory SEED_DATA=true java -jar target/product-catalog-ws.jar` |
| WSDL | http://localhost:8080/ws/v1/products?wsdl |
| Demo client | `java -cp target/product-catalog-ws.jar com.infy.catalog.client.CatalogClient` |
| Smoke test | `scripts/smoke-test.sh http://localhost:8080` |
| Update WSDL baseline | `scripts/update-contract.sh` (only for intended, compatible changes) |
| Render k8s | `kubectl kustomize k8s/overlays/k3s` |

`Makefile` wraps these (`make help`). Full setup lives in `HOW_TO_RUN.md`.

## Architecture

```
ws/            SOAP adapter: SEI (ProductCatalogPort), endpoint, JAXB DTOs, faults, handler
  -> service/  business rules + validation (ProductService, ProductValidator, PagingPolicy)
    -> repository/  ProductRepository port: InMemoryProductRepository | JdbcProductRepository
domain/        records + domain exceptions, no framework imports
infra/         HTTP health handler, Hikari, Flyway, Metro/JUL settings
config/        AppConfig: typed env-var configuration, validated at startup
Application    composition root, HTTP server, graceful shutdown
```

Request flow: `CorrelationIdHandler` (traceId in MDC) → `ProductCatalogEndpoint` (maps DTO ↔ domain)
→ `ProductService` → repository. The endpoint turns every exception into a `CatalogFault`.

## Rules to follow

**Layering**
- `domain/` and `service/` must not import `jakarta.*`, JAXB, Metro or JDBC. If you need something
  SOAP-specific, it belongs in `ws/`.
- The endpoint stays thin: map, call the service, map back. No business logic in `ws/`.
- Constructor injection only; wiring happens in `Application`.

**SOAP contract (WSDL is a public API)**
- Namespace `http://catalog.infy.com/v1`, path `/ws/v1/products`. Changes must be **additive**:
  new optional elements, new operations, new enum values. Renaming/removing elements, adding
  required elements or changing types → create `v2` (new namespace + path) and keep v1 running.
- `WsdlContractIT` diffs the published WSDL/XSD against `src/test/resources/contract/`. If it
  fails, decide whether the change is compatible; only then run `scripts/update-contract.sh`
  and commit the baseline together with the code.
- JAXB DTOs are classes with a no-arg constructor (JAXB 4 does not support records). Use
  `@XmlAccessorType(FIELD)`, explicit `@XmlType(name, propOrder)`, and `@XmlElement(required = true)`
  only for truly mandatory fields. `java.time` needs `InstantXmlAdapter`.
- Package-level `@XmlSchema` (in `package-info.java`) keeps DTO/fault types in the v1 namespace.

**Errors**
- Business failures are domain exceptions (`ProductNotFoundException`, `DuplicateSkuException`,
  `InvalidRequestException`) mapped to `FaultCode` in `ProductCatalogEndpoint.call`.
- Every operation declares only `CatalogServiceException` (fault bean `CatalogFaultInfo`:
  code, message, details, traceId).
- Never put stack traces, SQL or class names in faults. Unexpected errors become
  `INTERNAL_ERROR` + traceId; the details go to the log. `SoapRuntimeDefaults` disables Metro's
  stack-trace capture: call it before any Metro class loads (main, tests).

**Validation & paging**
- All validation is in `ProductValidator` / `ProductService.search`; report all violations at once.
- Every string is bounded; limits match the column sizes in `V1__create_product_table.sql`.
- Page size: null/0 → `PAGE_SIZE_DEFAULT`, larger than `PAGE_SIZE_MAX` → capped. Pages are 0-based.
- Sorting only via the `SortField` whitelist, always with `id` as a tie-breaker.

**Persistence**
- Plain JDBC with bind parameters only. The only dynamic SQL is ORDER BY from the whitelist.
- Escape LIKE input with `JdbcProductRepository.escapeLike`.
- Flyway migrations are immutable once merged: add `V2__...`, never edit `V1`. Demo data lives in
  `db/seed` (applied only when `SEED_DATA=true`); keep `SeedData.java` in sync with it.
- Both repositories must pass `ProductRepositoryContract`. New repository behaviour → add a
  contract test first.

**Logging**
- SLF4J + Logback to stdout, one line per event, `traceId` from MDC. Never log payloads,
  passwords or personal data. `AppConfig.toString()` masks the DB password; keep it that way.

**Kubernetes**
- `k8s/base/app` is environment-neutral; put environment differences in `k8s/overlays/*`.
- Config via `configMapGenerator` (hash suffix → automatic rollout). Secrets are never committed
  for prod; the k3s overlay has dev-only generated credentials.
- Pods run as non-root (uid 10001) with a read-only root filesystem, and the namespace enforces
  the `restricted` Pod Security Standard. Keep new manifests compliant.

## Testing expectations

- Service rules → plain unit tests (`ProductServiceTest`), no mocks needed (in-memory repo).
- Storage → `ProductRepositoryContract` (runs on the in-memory store and on PostgreSQL via Testcontainers).
- SOAP behaviour (faults, headers, paging over the wire) → `ProductCatalogEndpointIT`.
- JaCoCo line coverage gate: `coverage.minimum` in `pom.xml` (0.65).

## Gotchas

- `javax.jws` / `javax.xml.ws` are the old namespaces; this project uses `jakarta.*` (Metro 4).
- The fat jar needs shade's `ServicesResourceTransformer` (Metro, JAXB, Flyway and SLF4J use `META-INF/services`).
- HTTP header names come back normalized by the JDK server (`X-correlation-id`); compare case-insensitively.
- SOAP faults are HTTP 500 by spec; the `code` in the fault detail is what clients branch on.
- `listProducts`/`searchProducts` return `<result><items><product>…`; an empty page still has `<items/>`.
- In-memory store = per-pod data. Only use `STORE_TYPE=memory` locally.

## Known gaps / roadmap

- No authentication yet. Planned: WS-Security UsernameToken or mTLS at the ingress, plus
  per-operation authorization.
- Keyword search uses `LIKE '%…%'`, which does not use an index. For large catalogs, add a
  `pg_trgm` GIN index or full-text search.
- No metrics endpoint yet (Micrometer + Prometheus would fit).
- The upcoming REST version should reuse `service/`, `repository/` and `domain/` unchanged.
