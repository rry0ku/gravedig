# Contributing to Gravedig

Thank you for your interest in contributing to **Gravedig** ("*Dig up the secrets your git history forgot to bury.*")!

Gravedig is a high-performance, open-source credential scanner and excavation tool designed with a zero-telemetry, zero-foreign-execution philosophy.

---

## 🛠️ Prerequisites

- **Java Development Kit (JDK)**: Java 17 or higher (Temurin, Corretto, or OpenJDK).
- **Apache Maven**: Version 3.9+ (`mvn`).
- **Node.js**: Version 20+ and npm (`npm`).
- **Git**: Version 2.25+.

---

## 🏗️ Building From Source

Gravedig is structured as a Maven multi-module project:

```
gravedig/
├── core/         # Detection rules, entropy calculator, JGit excavation engine, formatters
├── server/       # Javalin REST & WebSocket server, SQLite persistence layer, UI assets
├── cli/          # Picocli command line interface, uber-jar packaging
└── frontend/     # Svelte 5 + Vite + TypeScript dark-mode web GUI
```

### 1. Build the Frontend
```bash
cd frontend
npm install
npm run build
```
The compiled static assets are placed in `server/src/main/resources/public`.

### 2. Build the Multi-Module Project & Shaded JAR
```bash
cd ..
mvn clean package
```
This produces the all-in-one shaded executable jar at:
`cli/target/gravedig.jar`

### 3. Run the Test Suite
```bash
mvn test
```

---

## 🔒 Security Architecture Rule (Zero Process Execution)

> [!CAUTION]
> **Strict Policy**: Gravedig **NEVER** invokes shell commands or external processes (`ProcessBuilder`, `Runtime.getRuntime().exec()`, etc.).
> All git repository traversal is strictly executed via the pure-Java **JGit** library.
> Any code that introduces `ProcessBuilder` or `Runtime.exec` will fail the automated `ForbiddenExecutionSecurityTest` suite in CI.

---

## 📜 Adding or Extending Secret Detection Rules

Rules are located in `core/src/main/resources/rules/rules.yml`.

### Rule Schema

```yaml
rules:
  - id: custom-service-token
    name: "Custom Service API Token"
    severity: HIGH # CRITICAL, HIGH, MEDIUM, LOW, INFO
    description: "Detects leaked authentication tokens for Custom Service."
    keywords:
      - "cstk_"
      - "cstk-live"
    pattern: 'cstk_(?:live|test)_[0-9a-zA-Z]{32}'
    entropyThreshold: 3.5 # Minimum Shannon entropy in bits/character
    remediation: "Immediately rotate this token in the developer portal and purge from git history."
```

### Rule Guidelines
- **Keywords**: Fast pre-filtering strings checked before regex evaluation. Always include representative prefixes or keys to maintain high scanning throughput.
- **Entropy Threshold**: Optional Shannon entropy threshold (0.0 - 8.0 bits/char) to weed out placeholder values like `cstk_live_00000000000000000000000000000000`.
- **Unit Tests**: Whenever adding a rule, add test assertions to `RuleEngineTest.java` verifying both positive hits and negative false positive rejections.

---

## 🧪 Performance Benchmarks

Run the built-in benchmark runner before submitting performance-related pull requests:

```bash
java -jar cli/target/gravedig.jar benchmark
```

---

## 📬 Pull Request Workflow

1. Fork the repository and create your branch from `main`:
   ```bash
   git checkout -b feat/my-new-rule
   ```
2. Verify all 40+ unit and integration tests pass:
   ```bash
   mvn test
   ```
3. Commit your changes with conventional commit messages (`feat: ...`, `fix: ...`, `docs: ...`).
4. Push your branch and open a Pull Request against `main`.
