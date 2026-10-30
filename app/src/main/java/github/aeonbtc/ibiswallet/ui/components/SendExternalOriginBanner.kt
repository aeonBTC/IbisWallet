package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import github.aeonbtc.ibiswallet.viewmodel.WalletViewModel

/**
 * Provenance banner shown when the send draft was auto-filled from an
 * external source (NFC tag tap or deep link) rather than typed or scanned
 * by the user on this surface. Labeled by origin element; cleared when the
 * user edits the fields.
 */
@Composable
fun SendExternalOriginBanner(
    origin: WalletViewModel.PendingSendOrigin,
    modifier: Modifier = Modifier,
) {
    val isNfc = origin == WalletViewModel.PendingSendOrigin.NFC
    val titleRes =
        if (isNfc) {
            R.string.send_external_origin_nfc_title
        } else {
            R.string.send_external_origin_link_title
        }
    val icon =
        if (isNfc) {
            Icons.Default.Sensors
        } else {
            Icons.Filled.Link
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = WarningYellow.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, WarningYellow.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WarningYellow,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelSmall,
                color = WarningYellow,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = stringResource(R.string.send_external_origin_message),
                style = MaterialTheme.typography.bodySmall,
                color = WarningYellow.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
