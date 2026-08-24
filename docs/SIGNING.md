# Reader APK signing policy

Reader APK updates must be signed with one stable app-signing certificate for the lifetime of the application.

## Current stable certificate

SHA-256 fingerprint:

`3e01a26d9f3f60aa84e50a150781b126d37fd4142eb35f6fcb5a1ea7c5e96978`

The private signing key is intentionally **not stored in this public repository**. It is retained separately as a private project artifact.

## CI rule

GitHub Actions debug APKs are build/test artifacts only. Their ephemeral debug certificates must not be treated as distributable update certificates.

The integration/release step must:

1. accept a CI-green APK;
2. remove/replace its temporary debug signature;
3. sign it with the stable Reader app-signing key using APK Signature Scheme v2 or newer;
4. verify the resulting signature and certificate fingerprint before distribution.

## Transition note

Prototype APKs prior to Reader 0.4 were built with ephemeral CI debug keys. Android therefore cannot update those prototypes in place with the stable-signed line unless the installed certificate matches. Do not uninstall a prototype containing unique local-only data without backing that data up first.
