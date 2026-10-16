# How to run

Paths from fastest to most complete:

| # | What | You need |
|---|---|---|
| 1 | [Run locally, in-memory](#1-run-locally-in-memory) | JDK 21, Maven |
| 2 | [Run with PostgreSQL (Docker Compose)](#2-run-with-postgresql-docker-compose) | Docker |
| 3 | [Call the service](#3-call-the-service) | curl / PowerShell / SoapUI |
| 4 | [Run the tests](#4-run-the-tests) | JDK 21, Maven, Docker (for ITs) |
| 5 | [Kubernetes on k3d (recommended local cluster)](#5-kubernetes-on-k3d-k3s-in-docker) | Docker, k3d, kubectl |
| 6 | [Kubernetes on native k3s (Linux / WSL2 / VM)](#6-kubernetes-on-native-k3s) | Linux host, Docker |
| 7 | [Jenkins CI/CD](#7-jenkins-cicd-local-lab) | Docker, path 5 done |
| 8 | [Production deployment](#8-production-deployment) | a real cluster + registry + managed PostgreSQL |

Configuration reference and troubleshooting are at the end.

---

## 0. Prerequisites

| Tool | Version | Check | Install |
|---|---|---|---|
| JDK | 21 | `java -version` | Temurin 21 |
| Maven | 3.9+ | `mvn -v` | maven.apache.org |
| Docker | 24+ (Compose v2, BuildKit) | `docker version`, `docker compose version` | Docker Desktop / Docker Engine |
| kubectl | 1.29+ | `kubectl version --client` | kubernetes.io/docs/tasks/tools |
| k3d | 5.7+ | `k3d version` | `brew install k3d` · `choco install k3d` · `curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh \| bash` |

**Windows:** run the `.sh` scripts from **Git Bash** or **WSL2**. If a script fails with
`bad interpreter: /bin/bash^M`, Git converted line endings: run
`git config core.autocrlf input` and re-clone (`.gitattributes` keeps them LF from then on).

---

## 1. Run locally (in-memory)

```bash
mvn -B -ntp clean package -DskipTests
STORE_TYPE=memory SEED_DATA=true java -jar target/product-catalog-ws.jar
# or: make run
```
PowerShell:
```powershell
$env:STORE_TYPE="memory"; $env:SEED_DATA="true"; java -jar target\product-catalog-ws.jar
```

| URL | What |
|---|---|
| http://localhost:8080/ws/v1/products | SOAP endpoint (POST) |
| http://localhost:8080/ws/v1/products?wsdl | WSDL |
| http://localhost:8080/ws/v1/products?xsd=1 | XML Schema |
| http://localhost:8080/health/live · /health/ready | Probes |

`SEED_DATA=true` loads 25 demo products across 5 categories. Stop with Ctrl+C (graceful shutdown).

---

## 2. Run with PostgreSQL (Docker Compose)

```bash
docker compose up -d --build        # PostgreSQL 16 + the service, demo data seeded
docker compose logs -f app          # Flyway migration + "Listening on port 8080"
docker compose down                 # stop (data kept)
docker compose down -v              # stop and delete the database volume
```
Port clash? `APP_PORT=9080 PG_PORT=15432 docker compose up -d`.

Connect to the DB: `docker compose exec postgres psql -U catalog -d catalog -c 'select count(*) from product;'`

---

## 3. Call the service

### Sample envelopes (curl)
`soap-samples/` holds ready-made requests for every operation, including search, pagination and faults:

```bash
./soap-samples/send.sh soap-samples/06-search-filter-sort-page.xml
./soap-samples/send.sh soap-samples/08-fault-not-found.xml
# another endpoint:
./soap-samples/send.sh soap-samples/05-list-products-page0.xml http://localhost:8081/ws/v1/products
```
Raw curl:
```bash
curl -s -X POST http://localhost:8080/ws/v1/products \
  -H 'Content-Type: text/xml; charset=utf-8' -H 'SOAPAction: ""' \
  --data-binary @soap-samples/07-search-keyword.xml
```
PowerShell:
```powershell
Invoke-WebRequest -Uri http://localhost:8080/ws/v1/products -Method Post `
  -ContentType 'text/xml; charset=utf-8' -Headers @{ SOAPAction = '""' } `
  -InFile soap-samples\06-search-filter-sort-page.xml | Select-Object -ExpandProperty Content
```

### Operations

| Operation | Input | Returns | Faults (`code`) |
|---|---|---|---|
| `createProduct` | `product` (sku, name, description?, category?, price, stock) | `product` | VALIDATION_FAILED, DUPLICATE_SKU |
| `getProduct` | `id` | `product` | PRODUCT_NOT_FOUND |
| `updateProduct` | `id`, `product` (full replace) | `product` | PRODUCT_NOT_FOUND, VALIDATION_FAILED, DUPLICATE_SKU |
| `deleteProduct` | `id` | empty response | PRODUCT_NOT_FOUND |
| `listProducts` | `page?`, `size?` | `result` page, sorted by id | VALIDATION_FAILED |
| `searchProducts` | `criteria`: keyword?, category?, minPrice?, maxPrice?, inStockOnly?, page?, size?, sortBy? (ID, NAME, PRICE, STOCK, CREATED_AT), sortOrder? (ASC, DESC) | `result` page | VALIDATION_FAILED |

Paging: `page` is 0-based; `size` defaults to 20 and is capped at 100. A page response looks like:
```xml
<result>
  <items><product>…</product>…</items>
  <page>0</page><size>3</size><totalElements>5</totalElements>
  <totalPages>2</totalPages><hasNext>true</hasNext><hasPrevious>false</hasPrevious>
</result>
```
Errors come back as HTTP 500 SOAP faults with a typed detail:
```xml
<ns2:CatalogFault xmlns:ns2="http://catalog.infy.com/v1">
  <code>VALIDATION_FAILED</code><message>Request validation failed</message>
  <details><detail>name is required</detail>…</details>
  <traceId>3f1c…</traceId>
</ns2:CatalogFault>
```
Send `X-Correlation-Id: <your-id>` to have it used as the `traceId` (it is echoed in the response header and the logs).

### Java client (JAX-WS proxy from the WSDL)
```bash
java -cp target/product-catalog-ws.jar com.infy.catalog.client.CatalogClient http://localhost:8080/ws/v1/products
```
Pages through Electronics by price, runs create → read → update → delete and prints two faults.

### SoapUI / Postman
Import `http://localhost:8080/ws/v1/products?wsdl` (SoapUI: *New SOAP Project*; Postman: *Import → link*). Requests for every operation are generated from the WSDL.

### Full smoke test
```bash
./scripts/smoke-test.sh http://localhost:8080                 # writes + cleans up a test product
./scripts/smoke-test.sh http://localhost:8080 --read-only     # no writes
```

---

## 4. Run the tests

```bash
mvn -B -ntp clean verify      # or: make test
```

| Suite | Runs in | Needs Docker |
|---|---|---|
| `*Test` (unit): config, service rules, in-memory repository contract | `test` phase | no |
| `ProductCatalogEndpointIT`: real HTTP + JAX-WS proxy, faults, headers, paging | `integration-test` | no |
| `JdbcProductRepositoryIT`: same repository contract on PostgreSQL 16 (Testcontainers) | `integration-test` | **yes** (skipped otherwise) |
| `WsdlContractIT`: published WSDL/XSD vs committed baseline | `integration-test` | no |

Reports: `target/surefire-reports`, `target/failsafe-reports`, coverage at `target/site/jacoco/index.html`.
Coverage gate: 65 % lines (`-Dcoverage.minimum=0.0` to bypass while experimenting).

### WSDL contract baseline (do this once)
The first build has no baseline, so `WsdlContractIT` is **skipped** with a message. Create and commit it:
```bash
./scripts/update-contract.sh
git add src/test/resources/contract && git commit -m "Add WSDL contract baseline"
```
From then on any change to the published contract fails the build until you review it and rerun
the script (only for backward-compatible changes; see `CLAUDE.md`).

---

## 5. Kubernetes on k3d (k3s in Docker)

k3d runs a real k3s cluster (Traefik ingress, local-path storage, metrics-server, NetworkPolicy)
as Docker containers. This works on Windows, macOS and Linux and needs no registry.

```bash
./infra/k3d/create-cluster.sh     # cluster "catalog", ingress -> localhost:8081   (make k3d-up)
./infra/k3d/deploy-local.sh       # docker build -> k3d image import -> kubectl apply -k   (make k3d-deploy)
```
What gets deployed (namespace `catalog`): the service (2 replicas, HPA 2-5, PDB),
PostgreSQL StatefulSet with a 1 Gi volume, a NetworkPolicy allowing only the app to reach
the DB, and a Traefik ingress for host `catalog.localhost`.

Try it:
```bash
kubectl -n catalog get pods,svc,ingress,hpa
curl -s -H 'Host: catalog.localhost' 'http://localhost:8081/ws/v1/products?wsdl' | head -5
./scripts/smoke-test.sh http://localhost:8081 --host catalog.localhost
```
Without the ingress:
```bash
kubectl -n catalog port-forward svc/product-catalog 8080:80
./scripts/smoke-test.sh http://localhost:8080
```
Useful:
```bash
kubectl -n catalog logs -l app.kubernetes.io/name=product-catalog -f --prefix
kubectl -n catalog exec -it product-catalog-postgres-0 -- psql -U catalog -d catalog
kubectl kustomize k8s/overlays/k3s        # see the rendered manifests
./infra/k3d/delete-cluster.sh             # remove everything   (make k3d-down)
```

---

## 6. Kubernetes on native k3s

For a Linux machine, a VM (e.g. Multipass) or WSL2 with systemd enabled:
```bash
./infra/k3s/install-k3s.sh                      # installs k3s, writes ~/.kube/config-k3s
export KUBECONFIG=~/.kube/config-k3s
./infra/k3s/deploy-local.sh                     # build, import into k3s containerd (sudo), deploy
./scripts/smoke-test.sh http://localhost --host catalog.localhost
```
Traefik listens on port 80 of the host. Uninstall with `/usr/local/bin/k3s-uninstall.sh`.

---

## 7. Jenkins CI/CD (local lab)

The `Jenkinsfile` pipeline: **checkout → `mvn verify` (unit, IT, Testcontainers, WSDL contract,
JaCoCo gate) → Docker image → Trivy scan → publish (k3d import or registry push) → manual
approval (prod only) → deploy with kustomize → smoke test → automatic `rollout undo` on failure.**

`infra/jenkins/` builds a Jenkins controller that already has JDK 21, Maven, Docker CLI, kubectl,
kustomize and k3d, installs the plugins and creates the job via Configuration as Code.

1. Push this project to a Git repository Jenkins can read (GitHub, GitLab, Azure Repos...).
2. Create the cluster first (it writes the kubeconfig Jenkins uses): `./infra/k3d/create-cluster.sh`
3. Configure and start Jenkins:
   ```bash
   cp infra/jenkins/.env.example infra/jenkins/.env    # set admin password + GIT_REPO_URL
   ./infra/jenkins/start-jenkins.sh                     # http://localhost:8090   (make jenkins-up)
   ```
4. Open job **product-catalog-ws → Build with Parameters**:
   - `DEPLOY_ENV=k3s`, `IMAGE_DELIVERY=k3d-import` for the local cluster
   - `DEPLOY_ENV=none` for build + test + scan only

| Credential id | Type | Created by | Used for |
|---|---|---|---|
| `kubeconfig-k3s` | Secret file | JCasC, from `infra/jenkins/secrets/kubeconfig-k3s` | deploy to k3d |
| `kubeconfig-prod` | Secret file | you (Manage Jenkins → Credentials) | deploy to prod |
| `registry-creds` | Username/password | you | `IMAGE_DELIVERY=registry-push` |

Notes:
- Jenkins runs as root with the host Docker socket mounted, which is acceptable **for a local lab only**.
  In a shared Jenkins use dedicated agents (Kubernetes pod agents or VM agents).
- To build automatically on push, add a webhook to your Git host or a `pollSCM` trigger.
- Trivy fails the build on fixable CRITICAL vulnerabilities. Rebuild to pick up patched base images.

---

## 8. Production deployment

`k8s/overlays/prod` is a template: app only (no in-cluster DB), 3-10 replicas spread across nodes,
NGINX ingress with TLS, and a managed PostgreSQL connection with `sslmode=require`.

1. Replace every `CHANGE_ME` in `k8s/overlays/prod/` (DB host, registry, hostname).
2. The cluster needs **ingress-nginx** and **metrics-server**, and must be able to pull from your
   registry (AKS: `az aks update --attach-acr <acr>`).
3. Create the secrets out of band. They are never stored in Git:
   ```bash
   kubectl create namespace catalog
   kubectl -n catalog create secret generic product-catalog-db \
     --from-literal=username='<db-user>' --from-literal=password='<db-password>'
   kubectl -n catalog create secret tls product-catalog-tls --cert=tls.crt --key=tls.key
   ```
   (Better: External Secrets Operator / Azure Key Vault CSI driver.)
4. Deploy through Jenkins with `DEPLOY_ENV=prod`, `IMAGE_DELIVERY=registry-push`, `REGISTRY=<your registry>`.
   The pipeline stops for manual approval, and the smoke test runs read-only.

Manual alternative:
```bash
cd k8s/overlays/prod && kustomize edit set image product-catalog-ws=<registry>/product-catalog-ws:<tag> && cd -
kubectl apply -k k8s/overlays/prod
kubectl -n catalog rollout status deployment/product-catalog
```

---

## Configuration reference

All settings are environment variables (ConfigMap `product-catalog-config` in Kubernetes).

| Variable | Default | Meaning |
|---|---|---|
| `APP_PORT` | `8080` | HTTP port (0 = random, used by tests) |
| `APP_CONTEXT_PATH` | `/ws/v1/products` | SOAP endpoint path |
| `STORE_TYPE` | `memory` | `memory` or `postgres` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | – | Required for `postgres` (password from a Secret) |
| `DB_POOL_SIZE` | `10` | Hikari max pool size |
| `SEED_DATA` | `false` | Load the 25 demo products |
| `HTTP_THREADS` | `32` | Request worker threads |
| `PAGE_SIZE_DEFAULT` / `PAGE_SIZE_MAX` | `20` / `100` | Paging defaults and cap |
| `SHUTDOWN_GRACE_SECONDS` | `10` (k8s: `20`) | Time allowed for in-flight requests on SIGTERM |
| `LOG_LEVEL` | `INFO` | Level for `com.infy.catalog` |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` | JVM flags (container image) |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Address already in use` | Another process on 8080: `APP_PORT=9080 java -jar …` |
| `bad interpreter: /bin/bash^M` | CRLF line endings, see Prerequisites (Windows) |
| `JdbcProductRepositoryIT` skipped | Docker not reachable from Maven. Start Docker or accept the skip; lower the gate with `-Dcoverage.minimum=0.0` if coverage drops |
| `WsdlContractIT` skipped | No baseline yet: `./scripts/update-contract.sh` and commit |
| `WsdlContractIT` fails | The published contract changed. Review it (see CLAUDE.md) and only then update the baseline |
| Pod `CreateContainerConfigError` | Secret `product-catalog-db` missing in namespace `catalog` |
| Pod `ImagePullBackOff` on k3d | Image not imported or wrong tag: `k3d image import product-catalog-ws:local -c catalog`; check `kubectl -n catalog describe pod …` |
| Pod not Ready, `/health/ready` 503 | App can't reach PostgreSQL: `kubectl -n catalog logs deploy/product-catalog`, check the DB pod and `DB_URL` |
| Postgres pod `Pending` | No default StorageClass (k3s/k3d have `local-path`; other clusters need one) |
| HPA shows `<unknown>` | metrics-server not installed (bundled with k3s) |
| Jenkins deploy: `connection refused` / TLS error | Jenkins not on the k3d network: rerun `./infra/jenkins/start-jenkins.sh`; or recreate the cluster with `create-cluster.sh` (adds the TLS SAN) |
| Jenkins: `permission denied … docker.sock` | The compose file runs Jenkins as root for the lab; don't remove `user: root` unless you add the docker group |
| Ingress returns 404 | Send the host header: `-H 'Host: catalog.localhost'` |
