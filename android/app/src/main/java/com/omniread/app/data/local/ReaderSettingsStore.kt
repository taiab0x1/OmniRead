package com.omniread.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
enum class ReaderFontChoice { SERIF, SANS, MONO }

@Singleton
class ReaderSettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val fontSizeKey = intPreferencesKey("font_size_sp")
    private val themeKey = stringPreferencesKey("reading_theme")
    private val lineSpacingKey = floatPreferencesKey("line_spacing")
    private val fontChoiceKey = stringPreferencesKey("font_choice")
    private val brightnessKey = floatPreferencesKey("brightness")
    private val lightweightKey = booleanPreferencesKey("lightweight_mode")

    val fontSizeFlow: Flow<Int> = ctx.readerStore.data.map { it[fontSizeKey] ?: DEFAULT_FONT_SP }

    val themeFlow: Flow<ReadingTheme> = ctx.readerStore.data.map {
        when (it[themeKey]) {
            "SEPIA" -> ReadingTheme.SEPIA
            "WHITE" -> ReadingTheme.WHITE
            else -> ReadingTheme.DARK
        }
    }

    val lineSpacingFlow: Flow<Float> = ctx.readerStore.data.map {
        it[lineSpacingKey] ?: DEFAULT_LINE_SPACING
    }

    val fontChoiceFlow: Flow<ReaderFontChoice> = ctx.readerStore.data.map {
        when (it[fontChoiceKey]) {
            "SANS" -> ReaderFontChoice.SANS
            "MONO" -> ReaderFontChoice.MONO
            else -> ReaderFontChoice.SERIF
        }
    }

    val brightnessFlow: Flow<Float> = ctx.readerStore.data.map {
        it[brightnessKey] ?: DEFAULT_BRIGHTNESS
    }

    val lightweightFlow: Flow<Boolean> = ctx.readerStore.data.map {
        it[lightweightKey] ?: false
    }

    suspend fun setFontSize(sp: Int) {
        ctx.readerStore.edit { it[fontSizeKey] = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP) }
    }

    suspend fun setTheme(theme: ReadingTheme) {
        ctx.readerStore.edit { it[themeKey] = theme.name }
    }

    suspend fun setLineSpacing(value: Float) {
        ctx.readerStore.edit { it[lineSpacingKey] = value.coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING) }
    }

    suspend fun setFontChoice(choice: ReaderFontChoice) {
        ctx.readerStore.edit { it[fontChoiceKey] = choice.name }
    }

    suspend fun setBrightness(value: Float) {
        ctx.readerStore.edit { it[brightnessKey] = value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS) }
    }

    suspend fun setLightweight(enabled: Boolean) {
        ctx.readerStore.edit { it[lightweightKey] = enabled }
    }

    companion object {
        const val MIN_FONT_SP = 14
        const val MAX_FONT_SP = 26
        const val DEFAULT_FONT_SP = 18
        const val MIN_LINE_SPACING = 1.35f
        const val MAX_LINE_SPACING = 2.1f
        const val DEFAULT_LINE_SPACING = 1.7f
        const val MIN_BRIGHTNESS = 0.2f
        const val MAX_BRIGHTNESS = 1f
        const val DEFAULT_BRIGHTNESS = 1f

        val PRESETS = listOf(
            "S" to 14,
            "M" to 18,
            "L" to 22,
            "XL" to 26,
        )
    }
}
