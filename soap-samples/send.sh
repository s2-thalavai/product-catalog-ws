#!/usr/bin/env bash
# Send one sample envelope and pretty-print the response.
#   soap-samples/send.sh soap-samples/06-search-filter-sort-page.xml [ENDPOINT]
set -euo pipefail
FILE="${1:?usage: send.sh <envelope.xml> [endpoint]}"
ENDPOINT="${2:-http://localhost:8080/ws/v1/products}"

RESPONSE=$(curl -sS -X POST "$ENDPOINT" \
  -H 'Content-Type: text/xml; charset=utf-8' \
  -H 'SOAPAction: ""' \
  -H "X-Correlation-Id: sample-$$" \
  -w '\nHTTP %{http_code}' \
  --data-binary @"$FILE")

BODY="${RESPONSE%$'\n'HTTP *}"
STATUS="${RESPONSE##*$'\n'}"
if command -v xmllint >/dev/null 2>&1; then
  echo "$BODY" | xmllint --format -
else
  echo "$BODY"
fi
echo "$STATUS"
