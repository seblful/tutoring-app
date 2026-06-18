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

`CONTEXT.md` is the authoritative reference for this project — what it does, its architecture, module map, key design decisions, and non-goals. If it does not exist, create it before starting work. Read it at the start of any non-trivial task. Keep it high-level and project-relevant, not detailed — capture decisions and structure that aren't obvious from the code, not things the code already states. Update it when modules, design decisions, non-goals, or data flow change.
