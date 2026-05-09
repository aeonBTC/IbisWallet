package github.aeonbtc.ibiswallet.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class AppLocale(
    val storageValue: String,
    val languageTag: String,
) {
    ENGLISH("en", "en"),
    SPANISH("es", "es"),
    BRAZILIAN_PORTUGUESE("pt-BR", "pt-BR"),
    RUSSIAN("ru", "ru"),
    ;

    companion object {
        fun fromStorageValue(value: String?): AppLocale {
            return entries.firstOrNull { it.storageValue == value } ?: ENGLISH
        }

        fun createLocalizedContext(
            context: Context,
            locale: AppLocale,
        ): Context {
            val configuration = Configuration(context.resources.configuration)
            val selectedLocale = Locale.forLanguageTag(locale.languageTag)
            configuration.setLocales(LocaleList(selectedLocale))
            configuration.setLayoutDirection(selectedLocale)
            return context.createConfigurationContext(configuration)
        }
    }
}
