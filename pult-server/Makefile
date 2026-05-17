.PHONY: format
format: ## Auto-format and auto-fix code (for local development)
	@echo "Formatting code: Ruff"
	@uv run ruff format
	@echo "Auto-fixing code: Ruff"
	@uv run ruff check --fix

.PHONY: lint
lint: ## Check code without mutating (for CI/CD)
	@echo "Checking lock file consistency"
	@uv lock --locked
	@echo "Linting code: Ruff"
	@uv run ruff check
	@echo "Checking formatting: Ruff"
	@uv run ruff format --check
	@echo "Static type checking: ty"
	@uv run ty check
	@echo "Checking dependencies: deptry"
	@uv run deptry .

.PHONY: check
check: format lint ## Run all local checks and fixes
	@echo "========= Clear! =========="

.PHONY: help
help:
	@uv run python -c "import re; \
	[[print(f'\033[36m{m[0]:<20}\033[0m {m[1]}') for m in re.findall(r'^([a-zA-Z_-]+):.*?## (.*)$$', open(makefile).read(), re.M)] for makefile in ('$(MAKEFILE_LIST)').strip().split()]"

.DEFAULT_GOAL := help