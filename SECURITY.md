# Security

This is a development preview, not a security-certified production reader. Please do not include API keys, signing material, book content or private reading backups in public issues.

Do not commit any keystore or API credential. Configure signing outside this repository, as described in `docs/BUILDING.md`. Android certificates are public; signing private keys are not.

The application sends only requested book fragments and bounded context to the selected translation provider when translation is enabled. See `docs/PRIVACY.md` for data flow and `docs/RELEASE_AUDIT.md` for the actual checks and their limits.

Run `python3 scripts/check_public_files.py` before pushing. Its pattern scan is a guard against common accidental disclosures, not a complete vulnerability scanner.

Only the latest code is the recommended development baseline. Historical tags preserve earlier behavior and known defects; v1.0.0 has a known EPUB import issue.
