#!/usr/bin/env bash
set -euo pipefail

# Complete Gravedig Interactive Demo
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_FILE="${ROOT_DIR}/cli/target/gravedig.jar"
DEMO_REPO="/tmp/gravedig-demo-repo"

cat << 'EOF'
  ____                                 _ _       
 / ___|_ __ __ ___   _____  __| (_) __ _ 
| |  _| '__/ _` \ \ / / _ \/ _` | |/ _` |
| |_| | | | (_| |\ V /  __/ (_| | | (_| |
 \____|_|  \__,_| \_/ \___|\__,_|_|\__, |
                                   |___/ 
 "Dig up the secrets your git history forgot to bury."

EOF

# 1. Check or build jar
if [ ! -f "${JAR_FILE}" ]; then
  echo "[-] gravedig.jar not found. Compiling project with Maven..."
  cd "${ROOT_DIR}"
  mvn clean package -DskipTests
fi

# 2. Generate synthetic git repository
echo "[1/4] Generating synthetic git repository with ALIVE and BURIED secrets..."
bash "${SCRIPT_DIR}/generate-demo-repo.sh" "${DEMO_REPO}"

# 3. Run CLI scan with full git history
echo ""
echo "[2/4] Executing Gravedig CLI Excavation Scan (--history)..."
java -jar "${JAR_FILE}" scan "${DEMO_REPO}" --history || true

# 4. Generate SARIF and HTML exports
echo ""
echo "[3/4] Generating SARIF 2.1.0 and Interactive HTML Audit Reports..."
java -jar "${JAR_FILE}" scan "${DEMO_REPO}" --history --format sarif --output /tmp/gravedig-audit.sarif || true
java -jar "${JAR_FILE}" scan "${DEMO_REPO}" --history --format html --output /tmp/gravedig-audit.html || true
echo "✓ SARIF saved to: /tmp/gravedig-audit.sarif"
echo "✓ HTML Report saved to: /tmp/gravedig-audit.html"

# 5. Launch GUI Option
echo ""
echo "[4/4] Gravedig Local GUI"
echo "To explore findings in the dark cyber web dashboard, run:"
echo "  java -jar ${JAR_FILE} ui"
echo ""
echo "Demo complete."
