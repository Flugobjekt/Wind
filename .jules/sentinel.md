## 2025-02-27 - [Secure Error Handling in Commands]
**Vulnerability:** Command responses in `WindContextReport.java` were exposing raw Java exception stack details directly to the end-user.
**Learning:** Returning exception classes and messages to users reveals internal implementation details, which is an information disclosure vulnerability.
**Prevention:** Fail securely by returning generic error messages to the user while logging the full exception stack trace internally for administrative debugging.
