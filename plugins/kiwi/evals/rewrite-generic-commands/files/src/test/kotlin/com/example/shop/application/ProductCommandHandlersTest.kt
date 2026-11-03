package com.example.shop.application

import com.example.shop.domain.command.UpdateProduct
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.fail

@SpringBootTest
class ProductCommandHandlersTest {
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var handler: UpdateProductHandler
    @Autowired lateinit var testData: ShopTestData

    @Test
    fun changePrice() {
        val product = testData.product()
        val command = UpdateProduct().apply {
            entityId = product.id
            jsonBody = objectMapper.readTree("""{ "price": 1299 }""")
        }
        val result = testData.tx { handler.handle(command, testData.admin) } ?: fail("missing result")
        testData.assertPriceChanged(result, 1299)
    }
}
