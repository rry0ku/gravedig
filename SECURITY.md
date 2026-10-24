# Security Policy

## Security Philosophy

**Gravedig** is built for developers and security engineers auditing sensitive codebases. As a security tool handling potentially sensitive secrets, Gravedig operates under a strict defensive posture:

1. **Zero Foreign Process Execution**: Gravedig runs entirely in-process using standard Java and JGit. It never calls `ProcessBuilder`, `Runtime.exec`, or launches system shell binaries.
2. **Strict Localhost Binding**: The local web UI and REST/WebSocket server bind exclusively to `127.0.0.1`—never `0.0.0.0` or public network interfaces.
3. **Cryptographically Secure Session Auth**: All `/api/*` and `/ws/*` endpoints require a 32-character hex authentication token generated via `SecureRandom` on startup.
4. **Secret Redaction by Default**: Discovered secrets are masked immediately upon pattern detection (`show first 4 and last 2 characters`). Raw secrets are never saved in database tables or logs.
5. **Zero Telemetry / Offline Guarantee**: Gravedig operates 100% offline. No telemetry, analytics, pingbacks, or external network requests are ever made.

---

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |

---

## Reporting a Vulnerability

If you discover a security vulnerability or bypass in Gravedig, please **do not** open a public issue.

Instead, please report it privately:
- **Email**: Send vulnerability reports to `security@gravedig.dev` or contact the maintainers via GitHub Security Advisories.
- **Details to Include**:
  - Description of the vulnerability.
  - Minimal reproducible example or proof-of-concept.
  - Affected versions.
  - Potential mitigation or remediation steps.

We will acknowledge receipt within 48 hours and work with you to release a fix promptly.
