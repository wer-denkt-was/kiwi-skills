package com.example.shop.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager

@Configuration
class SecurityConfig {
    @Bean
    fun userDetailsService(): InMemoryUserDetailsManager {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        return InMemoryUserDetailsManager(
            // Shop staff: may edit products
            User.withUsername("admin").password(encoder.encode("admin")).authorities("USER", "ADMIN").build(),
            // Support staff: read-only access, only the "ADMIN" authority is a Kiwi admin
            User.withUsername("support").password(encoder.encode("support")).roles("USER", "ADMIN").build(),
        )
    }
}
