## 2026-09-15 - [Secure Temporary File Creation]
**Vulnerability:** Files.createTempFile was being called without explicit file attributes in WindGlobalConfig.java. While Java 1.7+ often uses secure default permissions, relying on implicit defaults can trigger static analysis warnings and doesn't guarantee strict permissions across all environments.
**Learning:** Explicitly setting POSIX permissions (rw-------) using PosixFilePermissions when creating temporary configuration files provides defense in depth, especially when writing atomic configuration updates.
**Prevention:** Always specify restrictive POSIX permissions via FileAttribute when calling Files.createTempFile for sensitive data, ensuring a try-catch fallback for non-POSIX (Windows) compatibility.
