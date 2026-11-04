package com.foldfinance.core.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.foldfinance.core.data.db.dao.CategoryDao
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.model.DefaultCategories

/**
 * Сидирование [DefaultCategories.all]. Идемпотентно: вставка по уникальному `key` с IGNORE,
 * поэтому повторный запуск не создаёт дублей и не затирает переименования пользователя.
 */
internal object CategorySeeder {

    fun seed(db: SupportSQLiteDatabase) {
        DefaultCategories.all.forEachIndexed { index, seed ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (`key`, name, icon_key, color_argb, is_system, sort_order) " +
                    "VALUES (?, ?, ?, NULL, 1, ?)",
                arrayOf<Any?>(seed.key, seed.name, seed.iconKey, index),
            )
        }
    }

    suspend fun seed(dao: CategoryDao) {
        DefaultCategories.all.forEachIndexed { index, seed ->
            dao.insertIgnore(
                CategoryEntity(
                    key = seed.key,
                    name = seed.name,
                    iconKey = seed.iconKey,
                    colorArgb = null,
                    isSystem = true,
                    sortOrder = index,
                ),
            )
        }
    }
}
