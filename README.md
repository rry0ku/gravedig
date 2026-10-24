# Gravedig

```
  ____                                 _ _       
 / ___|_ __ __ ___   _____  __| (_) __ _ 
| |  _| '__/ _` \ \ / / _ \/ _` | |/ _` |
| |_| | | | (_| |\ V /  __/ (_| | | (_| |
 \____|_|  \__,_| \_/ \___|\__,_|_|\__, |
                                   |___/ 
```

> *"Dig up the secrets your git history forgot to bury."*

[![Build & Test](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java 17](https://img.shields.io/badge/java-17%2B-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Zero Foreign Execution](https://img.shields.io/badge/security-zero--process--execution-success.svg)](SECURITY.md)
[![OASIS SARIF 2.1.0](https://img.shields.io/badge/SARIF-2.1.0-orange.svg)]()

**Gravedig** is a high-performance secrets scanner and git excavation engine with a local web GUI. It scans local working directories and entire Git commit histories for leaked credentials (API keys, access tokens, private keys, database connection strings, passwords) and displays findings in a clean, purposeful dark-mode dashboard.

Gravedig distinguishes between **ALIVE** secrets (currently active in the working tree / HEAD) and **BURIED** secrets (deleted from files, but still lurking forever in historical commits).

---

## ⚡ The ALIVE vs. BURIED Paradigm

Traditional secret scanners only alert you to files currently on disk. But in real-world development:
1. A developer accidentally commits an AWS key or Stripe token.
2. Realizing the mistake 10 minutes later, they delete the credentials file and commit *"remove secret"*.
3. The repository looks clean in GitHub / GitLab.
4. **The secret is BURIED in git history**, completely visible to anyone who clones the repository.

Gravedig excavates the full commit tree, calculates diffs via pure Java AST parsing, and classifies every finding:
- <span style="color:#00ff88;font-weight:bold;">⚡ ALIVE</span>: Active in the working tree or latest branch commit. Immediate rotation required.
- <span style="color:#94a3b8;font-weight:bold;">⚰ BURIED</span>: Secret was purged from current files but exists in past commits, tags, or stashes. Requires git history rewriting (e.g. `git-filter-repo` / BFG).

---

## 🏛️ Architecture

```
                       +----------------------------------+
                       |        gravedig CLI / GUI        |
                       +-----------------+----------------+
                                         |
               +-------------------------+-------------------------+
               |                                                   |
     [ CLI (Picocli) ]                                  [ Server (Javalin 6) ]
     - Table output                                     - Strict 127.0.0.1 bind
     - SARIF 2.1.0 export                               - 32-char hex session auth
     - HTML audit export                                - SQLite local database
     - Pre-commit hook                                  - REST & WebSocket APIs
     - Benchmark suite                                  - Bundled Svelte 5 GUI
               |                                                   |
               +-------------------------+-------------------------+
                                         |
                              +----------v----------+
                              |    gravedig Core    |
                              +----------+----------+
                                         |
     +-------------------+---------------+---------------+-------------------+
     |                   |                               |                   |
[ Rule Engine ]   [ Entropy Filter ]           [ Fast Directory ]     [ JGit Engine ]
(15 YAML rules)   (Shannon Entropy)            (Parallel walker)     (Commit excavator)
```

---

## ✨ Features

- **Full Git History Excavation**: Analyzes all commits, tags, and stashes via Eclipse JGit without requiring an external `git` binary.
- **ALIVE vs. BURIED Secret Detection**: Know immediately whether an exposure is active in the codebase or hidden in past history.
- **Shannon Entropy Analysis**: Multi-character entropy calculation prevents false alarms on generic variable names and validates high-randomness tokens.
- **Local Web GUI**: Built with Svelte 5, Vite, and TypeScript. Zero third-party component libraries; custom SVG Risk Gauge, live WebSocket scan runner, filterable findings explorer, and rule sandbox.
- **Single Shaded Uber-JAR**: The entire frontend is bundled inside `gravedig.jar`. Run `java -jar gravedig.jar ui` and it serves the complete app on `127.0.0.1:7777`.
- **Pre-Commit Hook Integration**: Install with `gravedig hook install` to automatically block commits containing leaked secrets.
- **Enterprise Output Formats**: Export audit reports in **OASIS SARIF 2.1.0** (GitHub Code Scanning compatible), interactive self-contained **HTML**, **JSON**, or **CSV**.
- **Zero Foreign Process Execution**: Strictly verified by automated security test suites; zero calls to `ProcessBuilder` or `Runtime.exec`.
- **Zero Telemetry**: Completely air-gapped and private. No network calls, telemetry, or external dependencies.

---

## 📋 Built-in Rule Catalog

Gravedig ships with 15 production-ready detection rules:

| Rule ID | Name | Severity | Keywords / Prefixes |
|---|---|---|---|
| `aws-access-key-id` | AWS Access Key ID | **CRITICAL** | `AKIA`, `ASIA`, `ABIA`, `ACCA` |
| `aws-secret-access-key` | AWS Secret Access Key | **CRITICAL** | `aws_secret_access_key`, `aws_secret` |
| `github-pat` | GitHub Personal Access Token | **CRITICAL** | `ghp_`, `gho_`, `ghu_`, `ghs_`, `github_pat_` |
| `gitlab-pat` | GitLab Personal Access Token | **CRITICAL** | `glpat-` |
| `slack-token` | Slack Bot / User Token | **HIGH** | `xoxb-`, `xoxp-`, `xoxa-`, `xoxr-` |
| `slack-webhook` | Slack Incoming Webhook | **HIGH** | `hooks.slack.com` |
| `stripe-secret-key` | Stripe Secret Key | **CRITICAL** | `sk_live_`, `rk_live_` |
| `twilio-api-key` | Twilio API Key | **HIGH** | `SK` |
| `sendgrid-api-key` | SendGrid API Key | **HIGH** | `SG.` |
| `npm-access-token` | npm Access Token | **HIGH** | `npm_` |
| `pypi-api-token` | PyPI API Token | **HIGH** | `pypi-` |
| `jwt-token` | JSON Web Token (JWT) | **MEDIUM** | `eyJ` |
| `private-key-header` | Private Key Header | **CRITICAL** | `BEGIN PRIVATE KEY` |
| `db-connection-url` | Database Connection URL | **CRITICAL** | `postgresql://`, `mysql://`, `mongodb://` |
| `generic-secret-assignment` | Generic Password Assignment | **MEDIUM** | `password`, `secret`, `token`, `api_key` |

---

## 🚀 Quick Start

### 1. Build From Source

```bash
# Clone the repository
git clone https://github.com/gravedig/gravedig.git
cd gravedig

# Build frontend & package shaded uber-jar
cd frontend && npm install && npm run build && cd ..
mvn clean package -DskipTests
```

The executable uber-jar is created at: `cli/target/gravedig.jar`.

---

### 2. Launch Local Web GUI

```bash
java -jar cli/target/gravedig.jar ui
```

This starts the local Javalin server on `127.0.0.1:7777` and opens your browser:
- **Dashboard**: Risk Score Gauge (0–100), ALIVE vs. BURIED count cards, severity breakdown.
- **Live Scan Runner**: Real-time WebSocket streaming progress bar with active commit and findings tickers.
- **Findings Explorer**: Filterable table with search, severity filter, presence toggle, and pagination.
- **Inspection Drawer**: Secret reveal/mask toggle, commit blame information, remediation instructions, and status triage (`False Positive`, `Resolved`, `Accepted Risk`).
- **Rule Sandbox**: Interactive regex and entropy tester for custom secret rules.

Options:
```bash
# Run headless / without opening default browser
java -jar cli/target/gravedig.jar ui --no-browser --port 8080

# Specify custom SQLite database path
java -jar cli/target/gravedig.jar ui --db ./my-audits.db
```

---

### 3. CLI Scanning Commands

#### Scan Local Directory (Working Tree)
```bash
java -jar cli/target/gravedig.jar scan /path/to/project
```

#### Excavate Full Git Commit History
```bash
java -jar cli/target/gravedig.jar scan /path/to/project --history
```

#### Export to SARIF (GitHub Code Scanning)
```bash
java -jar cli/target/gravedig.jar scan . --history --format sarif --output audit.sarif
```

#### Export to Interactive HTML Report
```bash
java -jar cli/target/gravedig.jar scan . --history --format html --output report.html
```

#### Other Formats
```bash
# JSON output
java -jar cli/target/gravedig.jar scan . --format json

# CSV output
java -jar cli/target/gravedig.jar scan . --format csv
```

---

### 4. Git Pre-Commit Hook

Protect your repository from future leaks by installing the Gravedig pre-commit hook:

```bash
# Install hook into .git/hooks/pre-commit
java -jar cli/target/gravedig.jar hook install

# Specify minimum blocking severity (CRITICAL, HIGH, MEDIUM, LOW)
java -jar cli/target/gravedig.jar hook install --severity HIGH

# Uninstall hook
java -jar cli/target/gravedig.jar hook uninstall
```

---

### 5. Performance Benchmarks

Run on-demand throughput benchmarks:

```bash
java -jar cli/target/gravedig.jar benchmark
```

Output:
```
Running Gravedig Performance Benchmarks...
------------------------------------------------------------
  [Shannon Entropy Calculator]
    Operations: 50,000
    Throughput: 1,600,000 ops/sec (125.40 MB/s)
    avgNanosPerOp: 625.0 ns

  [Rule Engine Regex Evaluation (15 active rules)]
    Operations: 50,000
    Throughput: 145,000 ops/sec (18.20 MB/s)
    avgLatencyPerLine: 6.90 µs

  [JGit History Excavation & AST Diff Parsing]
    Operations: 100
    Throughput: 420 commits/s
------------------------------------------------------------
✓ All benchmarks completed successfully.
```

---

## ⚙️ Configuration (`.gravedig.yml`)

Place a `.gravedig.yml` file in your repository root to configure custom rules and exclusions:

```yaml
# Paths and patterns to ignore
exclude:
  - "**/node_modules/**"
  - "**/*.min.js"
  - "**/test/fixtures/**"

# Minimum Shannon entropy threshold (default: 3.0)
minEntropy: 3.2

# Custom rules
customRules:
  - id: internal-api-token
    name: "Acme Corp Internal Auth Token"
    severity: CRITICAL
    keywords: ["acme_sec_"]
    pattern: 'acme_sec_[0-9a-zA-Z]{32}'
    remediation: "Revoke token via the Acme Internal Vault."
```

### Baseline Suppression (`.gravedig-baseline.json`)

To accept known findings and avoid alerting on existing legacy code:
```bash
# Generate baseline of existing findings
java -jar cli/target/gravedig.jar scan . --baseline .gravedig-baseline.json --generate-baseline

# Run subsequent scans suppressing baseline items
java -jar cli/target/gravedig.jar scan . --baseline .gravedig-baseline.json
```

---

## 🔌 REST & WebSocket API

The local server exposes a REST and WebSocket API strictly bound to `127.0.0.1`. All endpoints require authentication via `X-Auth-Token` header, `Authorization: Bearer <token>`, or `?token=<token>`.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/health` | Health check (public) |
| `POST` | `/api/scans` | Launch new directory or git history scan |
| `GET` | `/api/scans` | List all historical scans |
| `GET` | `/api/scans/{id}` | Get scan summary and risk metrics |
| `GET` | `/api/scans/{id}/findings` | Filterable findings query |
| `PATCH`| `/api/findings/{id}` | Update triage status (`FALSE_POSITIVE`, `RESOLVED`, `ACCEPTED_RISK`) |
| `GET` | `/api/scans/{id}/export` | Export scan (`?format=sarif\|html\|json\|csv`) |
| `GET` | `/api/rules` | List all active detection rules |
| `POST` | `/api/rules/test` | Test rules or custom regex against code snippets |
| `WS` | `/ws/scan/{id}` | Real-time WebSocket streaming of scan progress and findings |

---

## 🎬 Reproducible Demo

Run the automated interactive demo:

```bash
bash scripts/demo.sh
```

This will:
1. Generate a synthetic multi-commit git repository with realistic credential leaks.
2. Simulate a developer deleting a secret file (burying it in git history).
3. Execute `gravedig scan --history` to excavate both ALIVE and BURIED secrets.
4. Export SARIF and HTML audit reports.

---

## 🤝 Contributing

Contributions are warmly welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, development setup, and pull request process.

---

## 🔒 Security

For security vulnerability reports and policies, please review [SECURITY.md](SECURITY.md).

---

## 📄 License

Gravedig is licensed under the [MIT License](LICENSE).
