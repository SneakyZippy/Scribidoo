# Agent Instructions & Engineering Standards

## 1. Code Architecture & File Hygiene
- **File & Module Limits:** Keep files strictly under 250–300 lines of code. If a file approaches 300 lines, extract helper modules or domain sub-services into cohesive boundaries before adding logic.
- **Single Responsibility & Cohesion:** Split monolithic classes into focused domain services or composable functions. Prefer composition and dependency injection over inheritance.
- **Function Granularity:** Limit functions to 30–40 lines. Each function should perform exactly one task with minimal cyclomatic complexity.

## 2. Implementation Integrity & Scope
- **No Stubs or Placeholders:** Never leave `TODO`s, mocks, or empty function bodies. Implement full logic or explicitly raise documented exceptions.
- **Surgical Edits:** Keep diffs focused strictly on the requested task. Do not touch or reformat unrelated functions.
- **Comment Preservation:** Preserve existing comments and docstrings. Only add comments to explain non-obvious *why*, not the *what*.

## 3. Types, Errors & Boundaries
- **Strict Typing:** No `any` or silent type-suppression flags (`@ts-ignore`, `noqa`) without explicit explanation.
- **No Silent Failures:** Never write empty catch blocks. Handle exceptions at boundaries and log actionable details.
- **Dependency Discipline:** Leverage the standard library and existing project dependencies before proposing new packages.

## 4. Verification & Testing
- **Test What You Change:** Accompany new logic and bug fixes with automated tests covering both expected behavior and edge cases.
- **Don't Weaken Tests:** Fix broken code; do not modify valid existing assertions to make a test pass.
