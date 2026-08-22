---
inclusion: auto
---

# Testing Standards

## When Implementing Features

Every time you implement a new feature, fix a bug, or modify existing functionality:

1. **Add or update backend tests** in `backend/src/test/java/com/myfinance/controller/` for any new/modified controller endpoints
2. **Add or update frontend tests** in `frontend/src/test/` or `frontend/src/components/` for any new/modified components
3. Tests should cover: creation, retrieval, update, deletion, edge cases, and error scenarios
4. Use `@WithMockUser` for all Spring Boot controller tests
5. Use `vitest` with `happy-dom` for frontend tests

## Test Structure

- Backend: Integration tests with `@SpringBootTest` + `@AutoConfigureMockMvc`
- Frontend: Unit tests for components + API export verification
- Test data should be self-contained (no dependency on DataInitializer)

## Running Tests

- Backend: `cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw test`
- Frontend: `cd frontend && npm test`

## End of Development Workflow

At the end of a development session:
1. Run all tests (`bash scripts/run-tests.sh`)
2. If any tests fail, fix the bugs before finishing
3. The Stop hook will automatically:
   - Run all tests and update `test-results.json`
   - Commit and push any pending changes

## Test Results

After running tests, update the test results JSON at `frontend/public/test-results.json` using the script at `scripts/run-tests.sh`. The Test Results page reads this file to display results.
