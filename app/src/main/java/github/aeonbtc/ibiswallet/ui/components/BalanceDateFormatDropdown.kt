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

private data class BalanceDateFormatOption(
    val id: String,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceDateFormatDropdown(
    currentFormat: String,
    onFormatSelected: (String) -> Unit,
    dense: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        listOf(
            BalanceDateFormatOption(
                id = SecureStorage.DATE_FORMAT_MM_DD_YY,
                name = stringResource(R.string.settings_balance_date_format_mm_dd_yy),
            ),
            BalanceDateFormatOption(
                id = SecureStorage.DATE_FORMAT_DD_MM_YY,
                name = stringResource(R.string.settings_balance_date_format_dd_mm_yy),
            ),
            BalanceDateFormatOption(
                id = SecureStorage.DATE_FORMAT_MONTH_DD_YYYY,
                name = stringResource(R.string.settings_balance_date_format_month_dd_yyyy),
            ),
            BalanceDateFormatOption(
                id = SecureStorage.DATE_FORMAT_YYYY_MM_DD,
                name = stringResource(R.string.settings_balance_date_format_yyyy_mm_dd),
            ),
        )
    val selectedOption =
        options.find { it.id == currentFormat }
            ?: options.first { it.id == SecureStorage.DATE_FORMAT_MONTH_DD_YYYY }

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
                            subtitle = "",
                            selected = option.id == currentFormat,
                        )
                    },
                    onClick = {
                        onFormatSelected(option.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (option.id == currentFormat) {
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
