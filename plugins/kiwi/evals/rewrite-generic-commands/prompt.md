---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

/kiwi:kiwi-upgrade 5

Phases 2 to 4 (build, setup, Jackson) are done by a colleague on another branch, so please only do phase 5 for
the Product commands and handlers here, including their test. No network in this environment: don't run Gradle,
and skip the commits. The frontend team can adapt to new JSON keys, but tell me which ones change.

Input files: the project in the current directory
