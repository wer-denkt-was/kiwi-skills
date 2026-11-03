package com.example.shop.domain.command

import com.example.shop.domain.Product
import com.example.shop.domain.ProductId
import com.example.shop.domain.schema.ProductFieldsets
import de.kiwi.api.domain.Roles
import de.kiwi.api.domain.UserDescriptor
import de.kiwi.api.domain.requireRole
import de.kiwi.api.generic.GenericCreate
import de.kiwi.api.generic.GenericDelete
import de.kiwi.api.generic.GenericUpdate

class CreateProduct : GenericCreate<Product>() {
    override fun entityId(entity: Product) = entity.id
    override val entityFieldset = ProductFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = user.requireRole(Roles.ADMIN)
}

class UpdateProduct : GenericUpdate<Product, ProductId>() {
    override val entityFieldset = ProductFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = user.requireRole(Roles.ADMIN)
}

class DeleteProduct : GenericDelete<Product, ProductId>() {
    override val entityFieldset = ProductFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = user.requireRole(Roles.ADMIN)
}
