package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.localization.AppLocale
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface

private data class LanguageOption(
    val locale: AppLocale,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    currentLocale: AppLocale,
    onLocaleSelected: (AppLocale) -> Unit,
) {
    val expandedState = remember { mutableStateOf(false) }
    val options =
        listOf(
            LanguageOption(
                locale = AppLocale.ENGLISH,
                name = stringResource(R.string.settings_language_english),
                description = stringResource(R.string.settings_language_english_description),
            ),
            LanguageOption(
                locale = AppLocale.SPANISH,
                name = stringResource(R.string.settings_language_spanish),
                description = stringResource(R.string.settings_language_spanish_description),
            ),
            LanguageOption(
                locale = AppLocale.BRAZILIAN_PORTUGUESE,
                name = stringResource(R.string.settings_language_brazilian_portuguese),
                description = stringResource(R.string.settings_language_brazilian_portuguese_description),
            ),
            LanguageOption(
                locale = AppLocale.RUSSIAN,
                name = stringResource(R.string.settings_language_russian),
                description = stringResource(R.string.settings_language_russian_description),
            ),
        )
    val selectedOption = options.find { it.locale == currentLocale } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expandedState.value,
        onExpandedChange = { expandedState.value = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expandedState.value,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expandedState.value,
            onDismissRequest = { expandedState.value = false },
            modifier =
                Modifier
                    .exposedDropdownSize(true)
                    .background(DarkSurface),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        DropdownOptionText(
                            title = option.name,
                            subtitle = option.description,
                            selected = option.locale == currentLocale,
                        )
                    },
                    onClick = {
                        onLocaleSelected(option.locale)
                        expandedState.value = false
                    },
                    leadingIcon = {
                        if (option.locale == currentLocale) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.common_selected),
                                tint = BitcoinOrange,
                            )
                        }
                    },
                )
            }
        }
    }
}
