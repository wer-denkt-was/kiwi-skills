package com.example.shop.domain.schema

import com.example.shop.domain.Category
import com.example.shop.domain.Product
import de.kiwi.api.schema.EntitySchema
import de.kiwi.api.schema.FieldsetsAvailable

object CategorySchema : EntitySchema<Category>() {
    val id by entityId()
    val name by column<String>(Category::name)
}

object ProductSchema : EntitySchema<Product>() {
    val id by entityId()
    val category by toOne(Product::category, CategorySchema)
    val categoryId by parentEntityId(Product::category)
    val name by column<String>(Product::name)
    val price by column<Int>(Product::price)
    val description by column<String?>(Product::description)
    val archived by column<Boolean>(Product::archived)
}

object CategoryFieldsets : FieldsetsAvailable<Category>(CategorySchema) {
    override val default by defineFieldset { includeAllColumns() }
}

object ProductFieldsets : FieldsetsAvailable<Product>(ProductSchema) {
    override val default by defineFieldset { includeAllColumns() }
}
