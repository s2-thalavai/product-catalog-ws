#!/usr/bin/env bash
# Build the image, load it into the k3d cluster (no registry needed) and deploy the k3s overlay.
#   ./infra/k3d/deploy-local.sh
set -euo pipefail

CLUSTER="${CLUSTER:-catalog}"
HTTP_PORT="${HTTP_PORT:-8081}"
IMAGE="${IMAGE:-product-catalog-ws:local}"
NAMESPACE=catalog
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT"
echo "==> Building $IMAGE"
docker build -t "$IMAGE" .

echo "==> Importing $IMAGE into k3d cluster '$CLUSTER'"
k3d image import "$IMAGE" --cluster "$CLUSTER"

echo "==> Applying k8s/overlays/k3s"
kubectl config use-context "k3d-${CLUSTER}" >/dev/null
kubectl apply -k k8s/overlays/k3s

# Same tag ("local") on every rebuild: force new pods so they pick up the new image.
kubectl -n "$NAMESPACE" rollout restart deployment/product-catalog
kubectl -n "$NAMESPACE" rollout status statefulset/product-catalog-postgres --timeout=180s
kubectl -n "$NAMESPACE" rollout status deployment/product-catalog --timeout=300s

kubectl -n "$NAMESPACE" get pods,svc,ingress
cat <<EOF

Deployed. Try:
  curl -s -H 'Host: catalog.localhost' "http://localhost:${HTTP_PORT}/ws/v1/products?wsdl" | head -5
  ./scripts/smoke-test.sh http://localhost:${HTTP_PORT} --host catalog.localhost
EOF
