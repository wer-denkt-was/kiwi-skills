package com.example.shop.application

import com.example.shop.domain.*
import com.fasterxml.jackson.databind.JsonNode
import de.kiwi.api.domain.DomainResult
import de.kiwi.api.domain.EntityId
import de.kiwi.api.domain.UserDescriptor
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.boot.test.context.TestComponent
import org.springframework.transaction.support.TransactionTemplate

@TestComponent
class ShopTestData(private val entityManager: EntityManager, private val transactions: TransactionTemplate) {
    val admin: UserDescriptor = UserDescriptor.ANONYMOUS // Replaced by a test admin in the real project

    fun product(): Product = tx {
        val category = entityManager.merge(Category(EntityId.generate(), name = "Tools"))
        entityManager.merge(Product.create(category, name = "Hammer", price = 999, description = null))
    }

    fun <T> tx(action: () -> T): T = transactions.execute { action() }!!

    fun assertPriceChanged(result: DomainResult<JsonNode>, expectedPrice: Int) {
        val changed = result.events.filterIsInstance<ProductPriceChanged>().single()
        assertEquals(expectedPrice, changed.newPrice)
    }
}
