---
name: run-and-measure-tests
description: How to correctly run the MyFinance backend and frontend test suites, measure JaCoCo coverage, keep it above 80%, and refresh the in-app Test Results page. Use whenever the user asks to run tests, check/raise coverage, or update the test results page.
---

# Run & Measure Tests

## Backend (MUST use JDK 17)

JDK 26 fails with an ICU `NullPointerException`. Always prefix with JDK 17:

```bash
cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw test jacoco:report
```

Free port 8080 first if a dev server is running (`lsof -ti:8080 | xargs kill`).

### Read line coverage

```bash
cat backend/target/site/jacoco/jacoco.csv | awk -F',' 'NR>1{m+=$8;c+=$9} END{printf "%.1f%%\n",(c*100.0)/(m+c)}'
```

### Find low-coverage classes to target

```bash
cat backend/target/site/jacoco/jacoco.csv | awk -F',' 'NR>1 {t=$8+$9; if(t>0){p=($9*100.0)/t; if(p<80) printf "%-40s %5.1f%% (%d/%d)\n",$3,p,$9,t}}' | sort -t' ' -k2 -n
```

`DataInitializer` and `AdminUserInitializer` are excluded from JaCoCo (init-only). `DataMigration`, `MyFinanceApplication` are init/bootstrap and not worth unit-testing — ignore them when choosing targets.

## Writing tests that pass

- Controllers: `extends BaseControllerTest`, `@SpringBootTest @AutoConfigureMockMvc`, `@WithMockUser` on every test, `repository.deleteAll()` in `@BeforeEach`. `BaseControllerTest` provides `testUser`.
- Pure classes (e.g. `GlobalExceptionHandler`, `JwtAuthFilter`): plain JUnit + **Mockito** (`@ExtendWith(MockitoExtension.class)`) — no Spring context needed, much faster.
- **BigDecimal JSON quirk:** entity endpoints serialize whole numbers as ints → `is(3200)`; summary endpoints keep DB scale → `is(3200.00)` / `closeTo(v, 0.01)`.
- Name supplemental gap tests `XxxExtraTest` to avoid clashing with the main `XxxControllerTest`.

## Frontend

```bash
cd frontend && npm run build   # tsc -b + vite build
cd frontend && npm test        # vitest --run (never watch mode)
```

## Refresh the in-app Test Results page

`generate-results.py` parses `backend/target/surefire-reports/*.xml` for backend suites and has hardcoded frontend suites + a `coverage` object. After a run:

1. Update the `coverage` numbers in `scripts/generate-results.py` if they changed.
2. Regenerate: `python3 scripts/generate-results.py` → writes `frontend/public/test-results.json`.
3. Verify: total/passed/failed and that new suites appear.

## Rules

- Target ≥ 80% backend line coverage. If below, add tests for the highest-missing-line classes first.
- Don't break `MultiTenantIsolationTest` or the `shouldReturn403ForNonAdmin...` guard tests.
- Clean up temp files. Do NOT auto-commit.
