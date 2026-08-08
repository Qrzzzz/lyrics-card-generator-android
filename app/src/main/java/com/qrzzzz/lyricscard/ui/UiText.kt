package com.qrzzzz.lyricscard.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.Serializable

/** Resource-backed presentation text that keeps Android Context out of state owners. */
sealed interface UiText : Serializable {
    data class Resource(
        @param:StringRes val id: Int,
        val arguments: List<Any> = emptyList(),
    ) : UiText

    /** Only for user/domain content that is already dynamic, never for static UI copy. */
    data class Dynamic(val value: String) : UiText

    data class Joined(
        val parts: List<UiText>,
        val separator: Resource,
    ) : UiText

    companion object {
        fun resource(@StringRes id: Int, vararg arguments: Any): UiText =
            Resource(id, arguments.toList())

        fun joined(@StringRes separatorId: Int, parts: List<UiText>): UiText =
            Joined(parts, Resource(separatorId))
    }
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> context.getString(
        id,
        *arguments.map { argument ->
            if (argument is UiText) argument.resolve(context) else argument
        }.toTypedArray(),
    )
    is UiText.Joined -> parts.joinToString(separator.resolve(context)) { it.resolve(context) }
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
