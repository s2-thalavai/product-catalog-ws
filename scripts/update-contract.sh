#!/usr/bin/env bash
# Regenerates the committed WSDL/XSD baseline used by WsdlContractIT.
# Only run this for intended, backward-compatible contract changes - then commit the result.
#   ./scripts/update-contract.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mvn -B -ntp -q verify \
  -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=WsdlContractIT -Djacoco.skip=true

mkdir -p src/test/resources/contract
rm -f src/test/resources/contract/*.wsdl src/test/resources/contract/*.xsd
cp target/contract/*.wsdl target/contract/*.xsd src/test/resources/contract/

echo "Baseline updated:"
ls -1 src/test/resources/contract/
git --no-pager diff --stat -- src/test/resources/contract/ 2>/dev/null || true
