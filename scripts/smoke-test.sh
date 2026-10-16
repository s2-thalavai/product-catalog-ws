#!/usr/bin/env bash
# End-to-end smoke test against a running deployment using raw SOAP over curl.
#
#   scripts/smoke-test.sh [BASE_URL] [--read-only] [--host HOSTNAME]
#
#   scripts/smoke-test.sh http://localhost:8080                                # local / compose
#   scripts/smoke-test.sh http://localhost:8081 --host catalog.localhost       # k3d ingress
#   scripts/smoke-test.sh https://catalog.example.com --read-only              # production
#
# Read-only mode only fetches the WSDL, lists and searches; it never writes data.
set -euo pipefail

BASE_URL="http://localhost:8080"
READ_ONLY=false
HOST_HEADER=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --read-only) READ_ONLY=true; shift ;;
    --host) HOST_HEADER=(-H "Host: $2"); shift 2 ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) BASE_URL="$1"; shift ;;
  esac
done

ENDPOINT="${BASE_URL%/}/ws/v1/products"
RESPONSE="$(mktemp)"
trap 'rm -f "$RESPONSE"' EXIT
FAILURES=0

pass() { echo "  PASS  $1"; }
fail() { echo "  FAIL  $1"; head -c 1500 "$RESPONSE"; echo; FAILURES=$((FAILURES + 1)); }

# soap <operation-body-xml>  -> prints HTTP status, response body in $RESPONSE
soap() {
  curl -sS -o "$RESPONSE" -w '%{http_code}' --max-time 15 ${HOST_HEADER[@]+"${HOST_HEADER[@]}"} \
    -H 'Content-Type: text/xml; charset=utf-8' -H 'SOAPAction: ""' \
    -H "X-Correlation-Id: smoke-$$" \
    -X POST "$ENDPOINT" \
    --data-binary "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:cat=\"http://catalog.infy.com/v1\"><soapenv:Header/><soapenv:Body>$1</soapenv:Body></soapenv:Envelope>" \
    || true   # connection errors print 000 and are reported as failures
}

expect() { # expect <description> <expected-status> <actual-status> <text-that-must-appear>
  if [[ "$3" == "$2" ]] && grep -q "$4" "$RESPONSE"; then pass "$1"; else fail "$1 (HTTP $3, expected $2 + '$4')"; fi
}

echo "Smoke testing $ENDPOINT (read-only: $READ_ONLY)"

status=$(curl -sS -o "$RESPONSE" -w '%{http_code}' --max-time 15 ${HOST_HEADER[@]+"${HOST_HEADER[@]}"} "$ENDPOINT?wsdl" || true)
expect "WSDL is published" 200 "$status" 'ProductCatalogService'

status=$(soap '<cat:listProducts><page>0</page><size>5</size></cat:listProducts>')
expect "listProducts returns a page" 200 "$status" '<totalElements>'

status=$(soap '<cat:searchProducts><criteria><keyword>mouse</keyword><sortBy>PRICE</sortBy><sortOrder>DESC</sortOrder><size>3</size></criteria></cat:searchProducts>')
expect "searchProducts runs" 200 "$status" '<totalPages>'

status=$(soap '<cat:searchProducts><criteria><minPrice>500</minPrice><maxPrice>100</maxPrice></criteria></cat:searchProducts>')
expect "invalid search returns VALIDATION_FAILED fault" 500 "$status" 'VALIDATION_FAILED'

if [[ "$READ_ONLY" == false ]]; then
  SKU="SMOKE-$(date +%s)-$$"
  status=$(soap "<cat:createProduct><product><sku>$SKU</sku><name>Smoke Test Item</name><category>Smoke</category><price>10.00</price><stock>1</stock></product></cat:createProduct>")
  expect "createProduct" 200 "$status" "$SKU"
  ID=$(sed -n 's:.*<id>\([0-9][0-9]*\)</id>.*:\1:p' "$RESPONSE" | head -n 1)

  if [[ -n "$ID" ]]; then
    status=$(soap "<cat:getProduct><id>$ID</id></cat:getProduct>")
    expect "getProduct $ID" 200 "$status" "$SKU"

    status=$(soap "<cat:updateProduct><id>$ID</id><product><sku>$SKU</sku><name>Smoke Test Item v2</name><category>Smoke</category><price>12.50</price><stock>2</stock></product></cat:updateProduct>")
    expect "updateProduct $ID" 200 "$status" '12.50'

    status=$(soap "<cat:createProduct><product><sku>$SKU</sku><name>Dup</name><price>1.00</price><stock>1</stock></product></cat:createProduct>")
    expect "duplicate SKU returns DUPLICATE_SKU fault" 500 "$status" 'DUPLICATE_SKU'

    status=$(soap "<cat:deleteProduct><id>$ID</id></cat:deleteProduct>")
    expect "deleteProduct $ID" 200 "$status" 'deleteProductResponse'

    status=$(soap "<cat:getProduct><id>$ID</id></cat:getProduct>")
    expect "deleted product returns PRODUCT_NOT_FOUND fault" 500 "$status" 'PRODUCT_NOT_FOUND'
  else
    fail "could not read the new product id"
  fi
fi

if [[ "$FAILURES" -gt 0 ]]; then
  echo "Smoke test FAILED ($FAILURES check(s))"
  exit 1
fi
echo "Smoke test passed"
