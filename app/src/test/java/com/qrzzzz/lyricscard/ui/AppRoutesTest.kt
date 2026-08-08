package com.qrzzzz.lyricscard.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppRoutesTest {
    @Test
    fun projectRoutesRoundTripWithoutRawRouteStrings() {
        val editor = EditorRoute("project.with-safe_id")
        val export = ExportRoute("project.with-safe_id")

        assertEquals(
            editor,
            Json.decodeFromString(EditorRoute.serializer(), Json.encodeToString(EditorRoute.serializer(), editor)),
        )
        assertEquals(
            export,
            Json.decodeFromString(ExportRoute.serializer(), Json.encodeToString(ExportRoute.serializer(), export)),
        )
        assertEquals(HomeRoute, Json.decodeFromString(HomeRoute.serializer(), Json.encodeToString(HomeRoute.serializer(), HomeRoute)))
        assertEquals(
            SettingsRoute,
            Json.decodeFromString(SettingsRoute.serializer(), Json.encodeToString(SettingsRoute.serializer(), SettingsRoute)),
        )
    }

    @Test
    fun navigationBackStackUsesTypedRoutesAndRestoresProjectIdArgument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lifecycleOwner = TestLifecycleOwner()
        val navController = NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setLifecycleOwner(lifecycleOwner)
            setViewModelStore(ViewModelStore())
            graph = createGraph(startDestination = HomeRoute) {
                composable<HomeRoute> { }
                composable<EditorRoute> { }
                composable<ExportRoute> { }
                composable<SettingsRoute> { }
            }
        }
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

        navController.navigate(EditorRoute("project/with unicode 空格"))

        assertTrue(navController.currentDestination?.hasRoute<EditorRoute>() == true)
        assertEquals(
            "project/with unicode 空格",
            navController.currentBackStackEntry?.toRoute<EditorRoute>()?.projectId,
        )

        navController.navigate(ExportRoute("project/with unicode 空格"))

        assertTrue(navController.currentDestination?.hasRoute<ExportRoute>() == true)
        assertEquals(
            "project/with unicode 空格",
            navController.currentBackStackEntry?.toRoute<ExportRoute>()?.projectId,
        )
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
