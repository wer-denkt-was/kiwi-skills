package com.example.shop.domain

import de.kiwi.api.domain.BaseEntity
import de.kiwi.api.domain.DomainEvent
import de.kiwi.api.domain.EntityId
import jakarta.persistence.*

@Embeddable
class CategoryId(value: Long) : EntityId(value)

@Entity
@Table(name = "category")
class Category(
    id: CategoryId,
    @Column(name = "name", nullable = false)
    var name: String,
) : BaseEntity<CategoryId>(id)

@Embeddable
class ProductId(value: Long) : EntityId(value)

@Entity
@Table(name = "product")
class Product(
    id: ProductId,

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "price_cents", nullable = false)
    var price: Int,

    @Column(name = "description", nullable = true)
    var description: String?,

    @Column(name = "archived", nullable = false)
    var archived: Boolean = false,
) : BaseEntity<ProductId>(id) {

    fun changePrice(newPrice: Int): ProductPriceChanged {
        require(newPrice >= 0) { "Price must not be negative" }
        val event = ProductPriceChanged(this, oldPrice = price, newPrice = newPrice)
        price = newPrice
        return event
    }

    fun archive(): ProductArchived {
        archived = true
        return ProductArchived(this)
    }

    companion object {
        fun create(category: Category, name: String, price: Int, description: String?) = Product(
            id = EntityId.generate(),
            category = category,
            name = name,
            price = price,
            description = description,
        )
    }
}

data class ProductPriceChanged(val product: Product, val oldPrice: Int, val newPrice: Int) : DomainEvent()

data class ProductArchived(val product: Product) : DomainEvent()
