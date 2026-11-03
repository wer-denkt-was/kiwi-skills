package com.example.shop

import de.kiwi.auditLog.KiwiAuditLogConfiguration
import de.kiwi.framework.KiwiFrameworkConfiguration
import de.kiwi.storage.KiwiStorageConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@Import(
    KiwiFrameworkConfiguration::class,
    KiwiAuditLogConfiguration::class,
    KiwiStorageConfiguration::class,
)
@EntityScan
@EnableJpaRepositories
class ShopApplication

fun main(args: Array<String>) {
    runApplication<ShopApplication>(*args)
}
