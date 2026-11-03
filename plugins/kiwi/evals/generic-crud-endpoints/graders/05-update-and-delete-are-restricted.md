---
type: llm
focus: trace
weight: 1
---

Update and delete are restricted to the owner or an admin (e.g. `user.isAdmin` or `Roles.ADMIN`), and violations throw a Kiwi domain exception rather than returning a response directly.
