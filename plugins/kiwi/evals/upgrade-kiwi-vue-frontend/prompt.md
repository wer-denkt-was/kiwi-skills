---
tags: [skill-creator]
max_turns: 60
timeout_seconds: 1800
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

/kiwi:kiwi-upgrade

This is the web frontend of our todo service. The backend was upgraded to Kiwi 1.5 last week (deployed together with
this frontend next Monday), and the backend team put the regenerated TypeScript model into `incoming/`. Please
upgrade the frontend. There's no network here, so **don't** run npm.

Input files: the project in the current directory
