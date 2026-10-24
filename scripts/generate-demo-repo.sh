#!/usr/bin/env bash
set -euo pipefail

# Generates a realistic synthetic Git repository to demonstrate ALIVE vs BURIED secret detection
TARGET_DIR="${1:-/tmp/gravedig-demo-repo}"

echo "============================================================"
echo "  GRAVEDIG: Generating Synthetic Demo Git Repository"
echo "  Target: ${TARGET_DIR}"
echo "============================================================"

rm -rf "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}"
cd "${TARGET_DIR}"

git init -b main
git config user.name "Alex Developer"
git config user.email "alex@example.corp"

# Commit 1: Initial boilerplate
cat << 'EOF' > package.json
{
  "name": "gravedig-demo-app",
  "version": "1.0.0",
  "main": "server.js",
  "dependencies": {
    "express": "^4.19.2"
  }
}
EOF

cat << 'EOF' > server.js
const express = require('express');
const app = express();
app.get('/health', (req, res) => res.json({ status: 'ok' }));
app.listen(3000, () => console.log('Server running on port 3000'));
EOF

git add .
git commit -m "feat: initial service boilerplate"

# Commit 2: Leaking AWS keys and Slack webhook (ALIVE at this point)
cat << 'EOF' > aws-credentials.env
# Production AWS keys for S3 backups
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
EOF

SLACK_HOOK="https://hooks.slack"
SLACK_HOOK="${SLACK_HOOK}.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"
cat << EOF > alerts.js
// Post deployment notifications to Slack
const SLACK_WEBHOOK_URL = '${SLACK_HOOK}';
module.exports = { SLACK_WEBHOOK_URL };
EOF

git add .
git commit -m "feat(integrations): configure AWS credentials and Slack deployment alerts"

# Commit 3: Adding database credentials
cat << 'EOF' > db.config.js
module.exports = {
  dbUrl: 'postgres://dbuser:P@ssw0rd2024Secure!@prod-cluster.internal:5432/core_db'
};
EOF

git add .
git commit -m "feat(database): configure production connection URI"

# Commit 4: Developer attempts to "bury" the AWS keys by deleting the file!
# NOTE: The file is deleted from HEAD, but remains forever in Git history (BURIED)!
rm aws-credentials.env
git add -A
git commit -m "fix(security): remove sensitive aws-credentials.env file"

# Commit 5: Developer adds Stripe API key in a payment module (ALIVE in HEAD)
STRIPE_KEY="sk_live"
STRIPE_KEY="${STRIPE_KEY}_51Abcd1234Efgh5678Ijkl9012Mnop3456Qrst"
cat << EOF > payments.js
const stripe = require('stripe');
const STRIPE_SECRET_KEY = '${STRIPE_KEY}';
const client = stripe(STRIPE_SECRET_KEY);
module.exports = { client };
EOF

git add .
git commit -m "feat(billing): integrate Stripe live billing gateway"

echo ""
echo "✓ Synthetic demo repository created successfully at: ${TARGET_DIR}"
echo ""
echo "Git History Summary:"
git log --oneline
echo ""
echo "Try running:"
echo "  gravedig scan ${TARGET_DIR} --history"
echo "  gravedig ui"
echo "============================================================"
