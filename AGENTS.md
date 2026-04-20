# Agent Instructions

## Build And Format

- Use the Gradle wrapper for repository tasks. Run `./gradlew`, not a system Gradle installation.
- Format Kotlin and Gradle Kotlin DSL changes with `./gradlew ktlintFormat` before finishing work.
- Validate changes with `./gradlew build` after formatting. Do not replace this with a narrower command unless the user explicitly asks for it.
- If formatting changes files, review the diff and rerun `./gradlew build`.

## Change Policy

- Keep changes small and aligned with the existing Gradle Kotlin DSL structure and naming.
- Prefer repository-defined plugins and automation over ad hoc scripts or alternate tooling.
- Treat updates as complete compatibility work: change the version, fix resulting breakages, and continue until `./gradlew build` passes.
- Do not weaken compiler or lint settings to make a change pass unless the user explicitly asks for that tradeoff.

## Warnings And Suppressions

- Treat suppressions as a last resort after attempting a real fix.
- Every suppression must have a short justification next to it. Avoid blanket or unexplained suppressions.

## Commits

- Follow the conventional commit format enforced by the repository hook: `type(scope): summary`.
- Use `type(scope)!: summary` plus a `BREAKING CHANGE:` footer for breaking changes.
- Keep commit subjects short, imperative, and specific.
- Use `chore(...)` or `ci(...)` for agent-instruction or automation-policy changes rather than `docs(...)`.
