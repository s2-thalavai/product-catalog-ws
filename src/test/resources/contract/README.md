# Contract baseline

`WsdlContractIT` compares the WSDL/XSD the service publishes with the files in this folder.

- No `.wsdl` here yet? The test is skipped. Generate the baseline once and commit it:
  `./scripts/update-contract.sh`
- Test failing after a code change? The published contract changed. If the change is
  intended **and backward compatible** (new optional elements, new operations, new enum
  values), run the script again and commit the updated files with your change.
  Anything else (renamed or removed elements, a new required element, changed types)
  breaks existing clients: publish it as `v2` instead (new namespace and endpoint path).
