#!/bin/bash
# MyFinance Test Runner
# Runs all backend and frontend tests and generates test-results.json
# Used by the Stop hook and can be run manually

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_FILE="$PROJECT_DIR/frontend/public/test-results.json"
JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"

echo "================================"
echo "  MyFinance Test Runner"
echo "================================"
echo ""

# ─── Backend Tests ───
echo "[1/2] Running backend tests..."
cd "$PROJECT_DIR/backend"
BACKEND_OUTPUT=$(JAVA_HOME="$JAVA_HOME" ./mvnw test -q 2>&1 || true)

# Parse backend results from surefire reports
BACKEND_RESULTS="[]"
if [ -d "target/surefire-reports" ]; then
  BACKEND_RESULTS=$(python3 -c "
import os, json, xml.etree.ElementTree as ET
suites = []
reports_dir = 'target/surefire-reports'
for f in sorted(os.listdir(reports_dir)):
    if f.startswith('TEST-') and f.endswith('.xml'):
        tree = ET.parse(os.path.join(reports_dir, f))
        root = tree.getroot()
        suite_name = root.get('name', '').split('.')[-1]
        tests = []
        for tc in root.findall('testcase'):
            name = tc.get('name', '')
            time_val = tc.get('time', '0')
            duration = f'{float(time_val)*1000:.0f}ms'
            failure = tc.find('failure')
            error = tc.find('error')
            status = 'fail' if failure is not None or error is not None else 'pass'
            tests.append({'name': name, 'status': status, 'duration': duration})
        if tests:
            suites.append({'name': suite_name, 'type': 'backend', 'tests': tests})
print(json.dumps(suites))
" 2>/dev/null || echo "[]")
fi

# ─── Frontend Tests ───
echo "[2/2] Running frontend tests with coverage..."
cd "$PROJECT_DIR/frontend"
FRONTEND_JSON_FILE="$PROJECT_DIR/frontend/test-output.json"
npx vitest --run --reporter=json --outputFile="$FRONTEND_JSON_FILE" --coverage 2>/dev/null || true

# Parse frontend results from vitest JSON output file
FRONTEND_RESULTS="[]"
if [ -f "$FRONTEND_JSON_FILE" ]; then
  FRONTEND_RESULTS=$(python3 << 'PYEOF'
import json
try:
    with open("/Users/mugu/Documents/Projects/myfinance/frontend/test-output.json") as f:
        data = json.load(f)
    suites = []
    for file_result in data.get('testResults', []):
        suite_name = file_result.get('name', '').split('/')[-1].replace('.test.tsx', '').replace('.test.ts', '')
        tests = []
        for assertion in file_result.get('assertionResults', []):
            name = assertion.get('title', assertion.get('fullName', ''))
            status = 'pass' if assertion.get('status') == 'passed' else 'fail'
            dur = assertion.get('duration', 0)
            duration = f'{dur}ms'
            tests.append({'name': name, 'status': status, 'duration': duration})
        if tests:
            suites.append({'name': suite_name, 'type': 'frontend', 'tests': tests})
    print(json.dumps(suites))
except Exception as e:
    print('[]')
PYEOF
)
  rm -f "$FRONTEND_JSON_FILE"
fi

# ─── Combine Results ───
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Write results parts to temp files and combine with python
echo "$BACKEND_RESULTS" > /tmp/myfinance_backend_results.json
echo "$FRONTEND_RESULTS" > /tmp/myfinance_frontend_results.json

python3 << PYEOF
import json, os, xml.etree.ElementTree as ET

with open('/tmp/myfinance_backend_results.json') as f:
    backend = json.loads(f.read().strip() or '[]')
with open('/tmp/myfinance_frontend_results.json') as f:
    frontend = json.loads(f.read().strip() or '[]')

all_suites = backend + frontend
total = sum(len(s['tests']) for s in all_suites)
passed = sum(1 for s in all_suites for t in s['tests'] if t['status'] == 'pass')
failed = sum(1 for s in all_suites for t in s['tests'] if t['status'] == 'fail')

# Parse backend coverage from JaCoCo XML
backend_coverage = None
jacoco_file = '$PROJECT_DIR/backend/target/site/jacoco/jacoco.xml'
if os.path.exists(jacoco_file):
    try:
        tree = ET.parse(jacoco_file)
        root = tree.getroot()
        for counter in root.findall('counter'):
            if counter.get('type') == 'LINE':
                missed = int(counter.get('missed', 0))
                covered = int(counter.get('covered', 0))
                total_lines = missed + covered
                if total_lines > 0:
                    backend_coverage = round((covered / total_lines) * 100, 1)
    except: pass

# Parse frontend coverage from coverage-summary.json
frontend_coverage = None
fe_cov_file = '$PROJECT_DIR/frontend/coverage/coverage-summary.json'
if os.path.exists(fe_cov_file):
    try:
        with open(fe_cov_file) as f:
            cov = json.load(f)
        frontend_coverage = cov.get('total', {}).get('lines', {}).get('pct')
    except: pass

result = {
    'timestamp': '$TIMESTAMP',
    'summary': {'total': total, 'passed': passed, 'failed': failed, 'suites': len(all_suites)},
    'coverage': {
        'backend': backend_coverage,
        'frontend': frontend_coverage,
    },
    'suites': all_suites
}

with open('$RESULTS_FILE', 'w') as f:
    json.dump(result, f, indent=2)

cov_str = f"Backend: {backend_coverage or '?'}% | Frontend: {frontend_coverage or '?'}%"
print(f'Results: {passed}/{total} passed, {failed} failed, {len(all_suites)} suites')
print(f'Coverage: {cov_str}')
PYEOF

rm -f /tmp/myfinance_backend_results.json /tmp/myfinance_frontend_results.json

echo ""
echo "Results saved to: $RESULTS_FILE"
echo "================================"
