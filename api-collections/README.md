# API collections

Two ready-made collections for the Product Catalog SOAP service. Both cover every operation,
search and pagination, and the fault cases, and both contain **automated assertions**, so they work
as a regression suite as well as a playground.

| | SoapUI | Bruno |
|---|---|---|
| File | `soapui/product-catalog-soapui-project.xml` | `bruno/product-catalog-soap/` (plain-text, Git-friendly) |
| Manual calls | 6 sample calls under the WSDL interface (Form/Outline editors) | 16 requests in 4 folders |
| Automated tests | Test suite: 3 test cases, 15 steps, 56 assertions | 75 tests (`tests {}` blocks) |
| Chaining | Property transfer `productId`, setup script makes a unique SKU | `bru.setVar("productId")`, unique SKU per run |
| CLI / CI | `testrunner.sh` (bundled with SoapUI) | `bru run` (npm `@usebruno/cli`) |

Start the service first, with demo data:
```bash
STORE_TYPE=memory SEED_DATA=true java -jar target/product-catalog-ws.jar
```

---

## SoapUI

**Open:** SoapUI 5.7+ (open source) or ReadyAPI → *File → Import Project* → `soapui/product-catalog-soapui-project.xml`.
The WSDL is loaded from `http://localhost:8080/ws/v1/products?wsdl`, so the service must be running.

**Manual calls:** expand *ProductCatalogPortBinding*. Each operation has a filled-in sample request:
double-click it and press ▶. To call another environment, choose a different endpoint from the request's address bar.

**Regression suite:** *Product Catalog Regression*:

| Test case | Steps |
|---|---|
| CRUD lifecycle | create → transfer id → get → update → duplicate SKU (fault) → delete → get deleted (fault) |
| List and search | first page · filter + sort + page (Groovy checks every row) · keyword · size cap 100 · page past the end |
| Faults | validation (4 details) · invalid price range · `X-Correlation-Id` echoed as `traceId` |

Assertions used: SOAP Response, (Not) SOAP Fault, XPath Match, Contains, Response SLA, Groovy Script.

Target another environment by changing the **project property `endpoint`** (Project → *Custom Properties*),
for example `http://localhost:18080/ws/v1/products` for a k3d port-forward.

**Command line / CI** (JUnit XML reports):
```bash
<SOAPUI_HOME>/bin/testrunner.sh -r -j -f target/soapui-reports \
  -Pendpoint=http://localhost:8080/ws/v1/products \
  api-collections/soapui/product-catalog-soapui-project.xml
# Windows: testrunner.bat with the same arguments
```

---

## Bruno

**Open:** Bruno desktop → *Open Collection* → select `bruno/product-catalog-soap/`.
Pick an environment (top right): `local`, `docker-compose` (both `:8080`) or `k3d-port-forward` (`:18080`).

```
product-catalog-soap/
├── collection.bru            shared headers: Content-Type text/xml, SOAPAction ""
├── environments/             local · docker-compose · k3d-port-forward
├── 0 Contract/               Get WSDL (all 6 operations present) · Health ready
├── 1 CRUD/                   Create → Get → Update → Duplicate SKU → Delete → Get deleted
├── 2 List and Search/        first page · filter+sort+page · keyword · size cap · past the end
└── 3 Faults/                 validation · invalid price range · correlation id
```

Run everything: right-click the collection → **Run**. The CRUD folder relies on running in order,
because *Create product* stores `{{sku}}` and `{{productId}}` for the next requests.

**Command line / CI:**
```bash
npm install -g @usebruno/cli
cd api-collections/bruno/product-catalog-soap
bru run --env local                                   # whole collection
bru run "1 CRUD" --env local                          # one folder
bru run --env local --reporter-junit ../../../target/bruno-results.xml
```
The exit code is non-zero when any test fails, so it can gate a pipeline stage.

---

## Notes

- **Faults are HTTP 500** (SOAP spec). The tests assert the typed `<code>` inside `CatalogFault` instead.
- The list and search tests check **rules** (filters, ordering, paging maths), not exact rows,
  so they still pass after you add or delete products.
- The CRUD tests create and delete their own product with a unique SKU; nothing is left behind
  unless a run stops halfway.
- For a deployment behind the k3d ingress, either port-forward
  (`kubectl -n catalog port-forward svc/product-catalog 18080:80`) or add the header
  `Host: catalog.localhost` and use `http://localhost:8081`.
