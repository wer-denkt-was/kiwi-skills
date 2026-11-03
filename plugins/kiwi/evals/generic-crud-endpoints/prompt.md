---
tags: [skill-creator]
max_turns: 40
timeout_seconds: 1200
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

We need REST endpoints for projects in this service: list/get, create, update and delete. Anyone logged in can
read and create projects (the creator becomes the owner), but only the owner or an admin may change or delete
one. Please implement it the way it's meant to be done in this framework. No network here, so don't run Gradle.

Input files: the project in the current directory
