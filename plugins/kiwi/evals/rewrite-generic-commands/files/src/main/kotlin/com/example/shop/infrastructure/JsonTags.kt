package com.example.shop.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

val tagMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

/** Tag names of a JSON array like `[{"name": "sale"}, {"name": "new"}]` */
fun JsonNode.tagNames(): List<String> = this.map { it.get("name").asText() }
