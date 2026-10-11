# Codex implementation guidance

## Start with the specifications

- Before implementing a feature, read the Markdown specifications in `docs/` and this file.
- Treat the specifications as the source of truth for product behavior, supported formats, platform scope, and acceptance criteria.
- If a requirement is missing or two documents conflict, do not guess at user-visible behavior. Call out the gap and ask for a product decision.
- Preserve existing repository content and conventions. Inspect the relevant files and build configuration before editing.

## Platform boundaries

- Keep Android and iOS requirements distinct where the specifications distinguish them. Follow the iOS guidance that assumes development on macOS.
- Use the platform and language choices already established by the repository and specifications. Do not introduce a cross-platform framework, media library, or architecture without support from those sources.
- Use operating-system media APIs when the specifications require them; document any third-party codec or library choice and its licensing implications before adding it.
- Keep format, codec, quality, metadata, performance, storage, and privacy behavior within the explicitly specified scope.

## Implementation practice

- Make focused changes that satisfy documented requirements and acceptance criteria.
- Handle user-selected media and output paths according to the platform guidance. Avoid overwriting or deleting user data unless the specification explicitly requires that behavior and the user confirms it in the product flow.
- Keep platform-specific code and shared logic clearly separated according to the established project structure.
- Update the relevant specification when implementation reveals a necessary, user-approved change to product behavior. Do not silently change the specification.

## Verification

- Inspect the repository's existing build and verification setup before choosing commands.
- Use the established build, lint, and test workflows for the affected platform when asked to verify a change. Do not add new verification tooling solely for convenience.
- Report what was verified and any platform-specific checks that could not be run.
