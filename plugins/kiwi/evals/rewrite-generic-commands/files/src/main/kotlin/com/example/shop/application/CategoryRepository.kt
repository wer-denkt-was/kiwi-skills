package com.example.shop.application

import com.example.shop.domain.Category
import com.example.shop.domain.CategoryId
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, CategoryId>
