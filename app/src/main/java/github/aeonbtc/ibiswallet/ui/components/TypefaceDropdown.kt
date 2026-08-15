package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.data.local.SecureStorage
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface

private data class TypefaceOption(
    val id: String,
    val name: String,
    val description: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypefaceDropdown(
    currentTypeface: String,
    onTypefaceSelected: (String) -> Unit,
    dense: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            TypefaceOption(
                id = SecureStorage.TYPEFACE_SYSTEM,
                name = stringResource(R.string.settings_typeface_system),
                description = stringResource(R.string.settings_typeface_system_description),
            ),
            TypefaceOption(
                id = SecureStorage.TYPEFACE_ATKINSON_HYPERLEGIBLE,
                name = stringResource(R.string.settings_typeface_atkinson_hyperlegible),
                description = stringResource(R.string.settings_typeface_atkinson_hyperlegible_description),
            ),
            TypefaceOption(
                id = SecureStorage.TYPEFACE_OPEN_RUNDE,
                name = stringResource(R.string.settings_typeface_open_runde),
                description = stringResource(R.string.settings_typeface_open_runde_description),
            ),
        )
    val selectedOption = options.find { it.id == currentTypeface } ?: options.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        CompactDropdownField(
            value = selectedOption.name,
            expanded = expanded,
            dense = dense,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
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
                            selected = option.id == currentTypeface,
                        )
                    },
                    onClick = {
                        onTypefaceSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentTypeface) {
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
