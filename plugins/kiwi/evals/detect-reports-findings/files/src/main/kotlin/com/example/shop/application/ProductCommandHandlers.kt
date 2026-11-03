package com.example.shop.application

import com.example.shop.domain.Product
import com.example.shop.domain.command.CreateProduct
import com.example.shop.domain.command.DeleteProduct
import com.example.shop.domain.command.UpdateProduct
import com.example.shop.domain.schema.ProductSchema
import de.kiwi.api.application.generic.GenericCreateHandler
import de.kiwi.api.application.generic.GenericDeleteHandler
import de.kiwi.api.application.generic.GenericUpdateHandler
import de.kiwi.api.domain.DomainResult
import de.kiwi.api.domain.UserDescriptor
import de.kiwi.api.util.requireById
import org.springframework.stereotype.Component

@Component
class CreateProductHandler(
    private val categories: CategoryRepository,
) : GenericCreateHandler<CreateProduct, Product>() {
    override fun createEntity(create: CreateBody, user: UserDescriptor) = DomainResult {
        val category = categories.requireById(create.require(ProductSchema.categoryId))
        Product.create(
            category = category,
            name = create.require(ProductSchema.name),
            price = create.require(ProductSchema.price),
            description = create[ProductSchema.description],
        )
    }
}

@Component
class UpdateProductHandler : GenericUpdateHandler<UpdateProduct, Product>() {
    override fun updateEntity(update: UpdateBody, entity: Product, user: UserDescriptor) = DomainResult { record ->
        update.patchField(ProductSchema.name)
        update.patchField(ProductSchema.description)
        update.patchNotNull(ProductSchema.price) { price -> record(entity.changePrice(price)) }
        update.patchNotNull(ProductSchema.archived) { archived -> if (archived) record(entity.archive()) }
    }
}

@Component
class DeleteProductHandler : GenericDeleteHandler<DeleteProduct, Product>() {
    override fun beforeDeleteEntity(delete: DeleteProduct, entity: Product, user: UserDescriptor) =
        DomainResult.empty
}
