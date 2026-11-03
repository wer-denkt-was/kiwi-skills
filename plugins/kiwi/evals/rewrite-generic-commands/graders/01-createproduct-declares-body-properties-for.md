---
type: llm
focus: trace
weight: 1
---

CreateProduct declares body properties for name, price and description using `by schema(ProductSchema.…)`, and the category using `by reference(ProductSchema.category)`, `by schema(ProductSchema.categoryId)` or `by property<CategoryId>("categoryId")`.
