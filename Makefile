SHELL := /bin/bash

init:
	./scripts/init.sh

frontend:
	yarn dev

backend:
	./mvnw clean spring-boot:run -Pdev

docker-run:
	./scripts/test.sh 2

docker-stop:
	./scripts/test.sh 4

backend-tests:
	./scripts/test.sh 1

package:
	./scripts/build.sh 0

snapshot:
	./scripts/build.sh 1

release:
	./scripts/build.sh 2

clean:
	./mvnw clean
	find . -name node_modules -type d -prune -or -name cache -type d -or -name dist -type d -or -name playwright-report -type d -or -name test-results -type d -or -name results.json -type f -or -name '*.bak' -type f -or -name '*.log' -type f -or -name '*.log.*.gz' -type f | xargs rm -rf

dedupe:
	./scripts/dedupe.sh

license-check:
	./scripts/license.sh 1

license-generate:
	./scripts/license.sh 3
