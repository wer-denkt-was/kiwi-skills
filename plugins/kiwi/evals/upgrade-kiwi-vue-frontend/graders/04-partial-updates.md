---
type: llm
focus: trace
weight: 1
---

TodoItemEditor.vue no longer spreads the loaded item (`...props.item`) into update bodies: `toggleDone` sends only `status`, and `save` sends only `title` and `content` (or only the changed ones).
