.PHONY: build test phase5-test frontend-install frontend-lint frontend-typecheck frontend-build frontend-check infra-up infra-down observability-up full-stack down logs

build:
	mvn clean verify

test:
	mvn test

phase5-test:
	mvn -pl event-service,attendee-service,payment-service,api-gateway -am test

frontend-install:
	npm --prefix frontend ci

frontend-lint:
	npm --prefix frontend run lint

frontend-typecheck:
	npm --prefix frontend run typecheck

frontend-build:
	npm --prefix frontend run build

frontend-check: frontend-install frontend-lint frontend-typecheck frontend-build

infra-up:
	docker compose up -d --wait

infra-down:
	docker compose down

observability-up:
	docker compose --profile observability up -d --wait

full-stack:
	docker compose --profile full-stack up -d --build --wait

down:
	docker compose --profile full-stack --profile observability down

logs:
	docker compose --profile full-stack --profile observability logs -f
