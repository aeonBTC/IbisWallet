package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.MainActivity
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.components.NfcStatusIndicator
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.ui.theme.WarningYellow
import kotlinx.coroutines.delay

/**
 * Auto-disarm for HCE PSBT sharing. HCE is promiscuous — any nearby reader
 * receives the payload while armed — so sharing must never outlive the
 * user's intent. Screens also disarm on dispose, cancel, and navigation.
 */
const val PSBT_HCE_SHARE_TIMEOUT_MS = 60_000L

/**
 * "Share via NFC" section for PSBT export cards (phone-to-phone handoff).
 * Arms HCE presentation of [payloadBase64] so another phone with PSBT import
 * armed can pull it with a tap. Mutual exclusion with tag read/write arming
 * is the caller's job via [onDisarmTagSession].
 *
 * @param payloadBase64 PSBT to present (unsigned, partial, or signed).
 * @param sizeOk false when the payload exceeds the NFC size cap — renders the
 * too-large note instead of the share button.
 * @param nfcShareAvailable HCE broadcast capability ([canBroadcast]).
 * @param shareArmed hoisted armed state owned by the caller.
 */
@Composable
fun PsbtNfcShareSection(
    payloadBase64: String,
    sizeOk: Boolean,
    nfcShareAvailable: Boolean,
    mainActivity: MainActivity?,
    shareArmed: Boolean,
    onShareArmedChange: (Boolean) -> Unit,
    onDisarmTagSession: () -> Unit,
) {
    LaunchedEffect(shareArmed, mainActivity) {
        if (shareArmed) {
            delay(PSBT_HCE_SHARE_TIMEOUT_MS)
            mainActivity?.disarmPsbtHceShare()
            onShareArmedChange(false)
        }
    }

    if (!nfcShareAvailable) {
        return
    }

    if (!sizeOk) {
        Text(
            text = stringResource(R.string.psbt_nfc_too_large),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        return
    }

    if (!shareArmed) {
        OutlinedButton(
            onClick = {
                onDisarmTagSession()
                if (mainActivity?.armPsbtHceShare(payloadBase64) == true) {
                    onShareArmedChange(true)
                }
            },
            enabled = mainActivity != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderColor),
        ) {
            Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = TextSecondary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.psbt_nfc_share),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
            )
        }
        return
    }

    NfcStatusIndicator(
        label = stringResource(R.string.psbt_nfc_tap_to_share),
        contentDescription = stringResource(R.string.psbt_nfc_tap_to_share),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.psbt_nfc_sharing_note),
        style = MaterialTheme.typography.bodySmall,
        color = WarningYellow,
        textAlign = TextAlign.Center,
    )
    TextButton(
        onClick = {
            mainActivity?.disarmPsbtHceShare()
            onShareArmedChange(false)
        },
    ) {
        Text(
            text = stringResource(R.string.psbt_nfc_cancel),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}
