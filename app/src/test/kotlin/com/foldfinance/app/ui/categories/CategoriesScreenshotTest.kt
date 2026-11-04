package com.foldfinance.app.ui.categories

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ui.analytics.InsightsSamples
import com.foldfinance.app.ui.components.categoryPaletteArgb
import com.foldfinance.core.model.Category
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** «Категории» in 3 Fold configurations × light/dark. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoriesScreenshotTest {

    private val categories = InsightsSamples.categories + Category(
        id = 11,
        key = "user_travel",
        name = "Путешествия",
        iconKey = "flight",
        colorArgb = categoryPaletteArgb(1),
        isSystem = false,
        sortOrder = 10,
    )

    /** The editor of a user category: list and editor side by side on the inner screen. */
    @Test fun editor() = captureInAllConfigs("categories_editor") {
        CategoriesScreen(
            state = CategoriesUiState(
                isLoading = false,
                categories = categories,
                draft = CategoryDraft(id = 11, name = "Путешествия", iconKey = "flight", colorArgb = categoryPaletteArgb(1)),
            ),
            actions = CategoriesActions(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }

    @Test fun list() = captureInAllConfigs("categories_list", configs = listOf(DeviceConfigs.Outer, DeviceConfigs.Inner)) {
        CategoriesScreen(
            state = CategoriesUiState(isLoading = false, categories = categories),
            actions = CategoriesActions(),
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
