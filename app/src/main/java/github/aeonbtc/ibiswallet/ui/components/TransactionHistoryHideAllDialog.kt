package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

@Composable
fun TransactionHistoryHideAllDialog(
    entryCount: Int,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }

    IbisConfirmDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.transaction_history_hide_all_title),
        message = stringResource(R.string.transaction_history_hide_all_message, entryCount),
        confirmText = stringResource(R.string.transaction_history_hide_all_confirm),
        confirmEnabled = acknowledged && entryCount > 0,
        confirmColor = ErrorRed,
        body = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { acknowledged = !acknowledged }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = acknowledged,
                    onCheckedChange = { acknowledged = it },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = ErrorRed,
                            uncheckedColor = TextSecondary,
                        ),
                )
                Text(
                    text = stringResource(R.string.transaction_history_hide_all_ack),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        onConfirm = onConfirm,
    )
}
