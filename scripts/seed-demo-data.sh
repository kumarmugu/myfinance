#!/usr/bin/env bash
# ============================================================================
# seed-demo-data.sh  —  Load demo data into the DEV H2 database ONLY.
# ============================================================================
# - Wipes existing data and reseeds every feature with demo records.
# - Preserves logins: admin/admin123 (ADMIN) and mugu/mugu (USER, owns data).
# - Standalone script (NOT a Java class, NOT run at app startup).
#
# HARD SAFETY RULES:
#   * Targets ONLY the dev DB: backend/data/myfinance
#   * Refuses to run if the DB URL/path references data-prod.
#   * Refuses to run if the backend appears to be running (port 8080 / H2 lock).
#
# Usage:  bash scripts/seed-demo-data.sh
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
SQL_TEMPLATE="$SCRIPT_DIR/demo-seed.sql"

# DEV database ONLY. Do not change this to data-prod.
DEV_DB_PATH="$BACKEND_DIR/data/myfinance"
DB_URL="jdbc:h2:file:${DEV_DB_PATH}"
DB_USER="sa"
DB_PASS=""
MUGU_UID="2"

# Guard: never allow a prod target.
case "$DB_URL$DEV_DB_PATH" in
  *data-prod*|*data_prod*)
    echo "ABORT: this script targets a prod database path. It is dev-only." >&2
    exit 1 ;;
esac

[[ -f "$SQL_TEMPLATE" ]] || { echo "ABORT: SQL template not found: $SQL_TEMPLATE" >&2; exit 1; }

if [[ ! -f "${DEV_DB_PATH}.mv.db" ]]; then
  echo "ABORT: dev DB not found at ${DEV_DB_PATH}.mv.db" >&2
  echo "Start the backend once so Hibernate creates the schema, then re-run." >&2
  exit 1
fi

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ABORT: port 8080 is in use — stop the backend before seeding." >&2
  exit 1
fi
if [[ -f "${DEV_DB_PATH}.lock.db" ]]; then
  echo "ABORT: H2 lock file present — the DB is in use." >&2
  exit 1
fi

JAVA_BIN="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java"
[[ -x "$JAVA_BIN" ]] || JAVA_BIN="$(command -v java || true)"
[[ -n "${JAVA_BIN:-}" ]] || { echo "ABORT: java not found (need JDK 17)." >&2; exit 1; }

M2="$HOME/.m2/repository"
H2_JAR="$(find "$M2/com/h2database/h2" -name 'h2-*.jar' 2>/dev/null | sort | tail -1)"
CRYPTO_JAR="$(find "$M2/org/springframework/security/spring-security-crypto" -name 'spring-security-crypto-*.jar' 2>/dev/null | sort | tail -1)"
CLOG_JAR="$(find "$M2/commons-logging" -name 'commons-logging-*.jar' 2>/dev/null | sort | tail -1)"
[[ -n "$H2_JAR" ]] || { echo "ABORT: H2 jar not found. Build the backend first (mvnw compile)." >&2; exit 1; }
[[ -n "$CRYPTO_JAR" && -n "$CLOG_JAR" ]] || { echo "ABORT: spring-security-crypto/commons-logging jars not found. Build the backend first." >&2; exit 1; }

echo "Generating BCrypt password hashes..."
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cat > "$WORK/GenHash.java" <<'JAVA'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GenHash {
  public static void main(String[] a) {
    System.out.println(new BCryptPasswordEncoder().encode(a[0]));
  }
}
JAVA
GEN_CP="$CRYPTO_JAR:$CLOG_JAR"
ADMIN_HASH="$("$JAVA_BIN" -cp "$GEN_CP" "$WORK/GenHash.java" 'admin123')"
MUGU_HASH="$("$JAVA_BIN" -cp "$GEN_CP" "$WORK/GenHash.java" 'mugu')"
if [[ "$ADMIN_HASH" != \$2* || "$MUGU_HASH" != \$2* ]]; then
  echo "ABORT: BCrypt hash generation failed." >&2; exit 1
fi

FINAL_SQL="$WORK/demo-seed.final.sql"
# '|' delimiter: BCrypt hashes contain '/' and '.' but never '|'.
sed -e "s|@ADMIN_HASH@|${ADMIN_HASH}|g" \
    -e "s|@MUGU_HASH@|${MUGU_HASH}|g" \
    -e "s|@UID@|${MUGU_UID}|g" \
    "$SQL_TEMPLATE" > "$FINAL_SQL"

echo "Seeding DEV database: ${DEV_DB_PATH}.mv.db"
"$JAVA_BIN" -cp "$H2_JAR" org.h2.tools.RunScript \
  -url "$DB_URL" -user "$DB_USER" -password "$DB_PASS" \
  -script "$FINAL_SQL"

echo ""
echo "Verifying seeded row counts..."
"$JAVA_BIN" -cp "$H2_JAR" org.h2.tools.Shell \
  -url "$DB_URL" -user "$DB_USER" -password "$DB_PASS" \
  -sql "SELECT 'app_users' AS tbl, COUNT(*) AS n FROM app_users
        UNION ALL SELECT 'owners', COUNT(*) FROM owners
        UNION ALL SELECT 'accounts', COUNT(*) FROM accounts
        UNION ALL SELECT 'assets', COUNT(*) FROM assets
        UNION ALL SELECT 'transactions', COUNT(*) FROM transactions
        UNION ALL SELECT 'holdings', COUNT(*) FROM holdings
        UNION ALL SELECT 'sold_positions', COUNT(*) FROM sold_positions
        UNION ALL SELECT 'dividends', COUNT(*) FROM dividends
        UNION ALL SELECT 'account_deposits', COUNT(*) FROM account_deposits
        UNION ALL SELECT 'bank_savings', COUNT(*) FROM bank_savings
        UNION ALL SELECT 'fixed_deposits', COUNT(*) FROM fixed_deposits
        UNION ALL SELECT 'generic_fixed_deposits', COUNT(*) FROM generic_fixed_deposits
        UNION ALL SELECT 'properties', COUNT(*) FROM properties
        UNION ALL SELECT 'precious_metals', COUNT(*) FROM precious_metals
        UNION ALL SELECT 'bonds', COUNT(*) FROM bonds
        UNION ALL SELECT 'home_loans', COUNT(*) FROM home_loans
        UNION ALL SELECT 'loan_payments', COUNT(*) FROM loan_payments
        UNION ALL SELECT 'insurance_policies', COUNT(*) FROM insurance_policies
        UNION ALL SELECT 'insurance_bonus_entries', COUNT(*) FROM insurance_bonus_entries
        UNION ALL SELECT 'retirement_fund_entries', COUNT(*) FROM retirement_fund_entries
        UNION ALL SELECT 'salary_records', COUNT(*) FROM salary_records
        UNION ALL SELECT 'work_experiences', COUNT(*) FROM work_experiences
        UNION ALL SELECT 'tax_records', COUNT(*) FROM tax_records
        UNION ALL SELECT 'budget_categories', COUNT(*) FROM budget_categories
        UNION ALL SELECT 'budget_plans', COUNT(*) FROM budget_plans
        UNION ALL SELECT 'budget_incomes', COUNT(*) FROM budget_incomes
        UNION ALL SELECT 'budget_allocations', COUNT(*) FROM budget_allocations
        UNION ALL SELECT 'expenses', COUNT(*) FROM expenses
        UNION ALL SELECT 'currency_rates', COUNT(*) FROM currency_rates
        UNION ALL SELECT 'user_currencies', COUNT(*) FROM user_currencies
        UNION ALL SELECT 'allocation_targets', COUNT(*) FROM allocation_targets
        UNION ALL SELECT 'net_worth_snapshots', COUNT(*) FROM net_worth_snapshots
        UNION ALL SELECT 'net_worth_config', COUNT(*) FROM net_worth_config
        UNION ALL SELECT 'banks', COUNT(*) FROM banks
        UNION ALL SELECT 'fd_holders', COUNT(*) FROM fd_holders
        UNION ALL SELECT 'audit_logs', COUNT(*) FROM audit_logs" 2>/dev/null

echo ""
echo "Done. Demo data seeded into the DEV database."
echo "  Logins:  admin / admin123   (ADMIN)"
echo "           mugu  / mugu        (USER, owns all demo data)"
