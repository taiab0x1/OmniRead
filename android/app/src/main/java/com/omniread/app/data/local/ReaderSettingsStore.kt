package com.omniread.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.readerStore by preferencesDataStore(name = "omniread_reader")

enum class ReadingTheme { DARK, SEPIA, WHITE }

@Singleton
class ReaderSettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val fontSizeKey = intPreferencesKey("font_size_sp")
    private val themeKey = stringPreferencesKey("reading_theme")

    val fontSizeFlow: Flow<Int> = ctx.readerStore.data.map { it[fontSizeKey] ?: DEFAULT_FONT_SP }

    val themeFlow: Flow<ReadingTheme> = ctx.readerStore.data.map {
        when (it[themeKey]) {
            "SEPIA" -> ReadingTheme.SEPIA
            "WHITE" -> ReadingTheme.WHITE
            else -> ReadingTheme.DARK
        }
    }

    suspend fun setFontSize(sp: Int) {
        ctx.readerStore.edit { it[fontSizeKey] = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP) }
    }

    suspend fun setTheme(theme: ReadingTheme) {
        ctx.readerStore.edit { it[themeKey] = theme.name }
    }

    companion object {
        const val MIN_FONT_SP = 14
        const val MAX_FONT_SP = 26
        const val DEFAULT_FONT_SP = 18

        val PRESETS = listOf(
            "S" to 14,
            "M" to 18,
            "L" to 22,
            "XL" to 26,
        )
    }
}
