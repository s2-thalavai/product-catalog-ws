# Shortcuts for the common commands (HOW_TO_RUN.md lists the raw commands too).
IMAGE   ?= product-catalog-ws:local
CLUSTER ?= catalog
URL     ?= http://localhost:8080

.PHONY: help build test run run-seeded client image compose-up compose-down \
        k3d-up k3d-deploy k3d-down jenkins-up smoke contract render clean

help:            ## List targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'

build:           ## Compile and package the fat jar (skips tests)
	mvn -B -ntp clean package -DskipTests

test:            ## Unit + integration tests, contract check, coverage gate
	mvn -B -ntp clean verify

run: build       ## Run locally with the in-memory store and demo data
	STORE_TYPE=memory SEED_DATA=true java -jar target/product-catalog-ws.jar

client:          ## Run the demo SOAP client against URL
	java -cp target/product-catalog-ws.jar com.infy.catalog.client.CatalogClient $(URL)/ws/v1/products

image:           ## Build the Docker image
	docker build -t $(IMAGE) .

compose-up:      ## PostgreSQL + service in Docker Compose
	docker compose up -d --build

compose-down:    ## Stop Compose and delete the database volume
	docker compose down -v

k3d-up:          ## Create the local k3s cluster (k3d)
	./infra/k3d/create-cluster.sh

k3d-deploy:      ## Build, import and deploy to the k3d cluster
	./infra/k3d/deploy-local.sh

k3d-down:        ## Delete the k3d cluster
	./infra/k3d/delete-cluster.sh

jenkins-up:      ## Start the local Jenkins (after k3d-up)
	./infra/jenkins/start-jenkins.sh

smoke:           ## Smoke test URL (e.g. make smoke URL=http://localhost:8080)
	./scripts/smoke-test.sh $(URL)

contract:        ## Regenerate the WSDL/XSD baseline (intended changes only)
	./scripts/update-contract.sh

render:          ## Print the k3s manifests without applying them
	kubectl kustomize k8s/overlays/k3s

clean:
	mvn -B -ntp -q clean
