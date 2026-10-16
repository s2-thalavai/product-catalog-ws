#!/usr/bin/env bash
# Native k3s: build the image with Docker, import it into k3s' containerd, deploy the k3s overlay.
#   KUBECONFIG=~/.kube/config-k3s ./infra/k3s/deploy-local.sh
set -euo pipefail

IMAGE="${IMAGE:-product-catalog-ws:local}"
NAMESPACE=catalog
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config-k3s}"

cd "$ROOT"
echo "==> Building $IMAGE"
docker build -t "$IMAGE" .

echo "==> Importing into k3s containerd (needs sudo)"
docker save "$IMAGE" | sudo k3s ctr images import -

echo "==> Applying k8s/overlays/k3s"
kubectl apply -k k8s/overlays/k3s
kubectl -n "$NAMESPACE" rollout restart deployment/product-catalog
kubectl -n "$NAMESPACE" rollout status statefulset/product-catalog-postgres --timeout=180s
kubectl -n "$NAMESPACE" rollout status deployment/product-catalog --timeout=300s
kubectl -n "$NAMESPACE" get pods,svc,ingress

cat <<EOF

Deployed. Traefik listens on port 80 of this host:
  curl -s -H 'Host: catalog.localhost' "http://localhost/ws/v1/products?wsdl" | head -5
  ./scripts/smoke-test.sh http://localhost --host catalog.localhost
EOF
