# AI Coding Guidelines

## Core Principles

### 1. Think Before Coding

**Don't assume. Surface tradeoffs. Ask when uncertain.**

- State assumptions explicitly
- Present multiple interpretations when they exist
- Push back if a simpler approach exists
- Name what's confusing before proceeding

### 2. Honest Pushback

**Flag bad ideas. Don't silently comply.**

Before executing, raise a concern if the request:
- Conflicts with an established project standard or convention
- Is an antipattern for this stack, context, or scale
- Would introduce security, correctness, or maintainability risk

State the concern in one sentence, name the specific tension, then proceed or ask — don't lecture. If the user confirms anyway, comply and move on.

### Comments and KDoc

Document non-obvious contracts, boundaries, platform behavior, and workarounds in the fewest words. Prefer clear names and tests over narration. Remove stale explanations and refactor history; keep rationale that guides future decisions.

### 3. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → Write tests for invalid inputs, then make them pass
- "Fix the bug" → Write a test that reproduces it, then make it pass

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
```

---

## Workflow

- **Tests**: Every feature and bug fix requires tests
- **Commits**: Commit only when explicitly asked one message before. Use [Conventional Commits](https://www.conventionalcommits.org/).

---

## Project Context

`docs/` is the reference for this project, one question per doc (listed in README → Docs). Before a non-trivial task, read the docs your change touches. Record decisions and structure the code doesn't state, and update the matching doc when modules, design decisions, non-goals, or data flow change.
