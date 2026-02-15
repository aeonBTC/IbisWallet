package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.TextPrimary
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.viewmodel.CertDialogState

/**
 * Dialog shown when connecting to a server whose certificate needs user approval.
 * - First use: informational, user must explicitly trust the certificate
 * - Cert changed: warning, indicates possible MITM attack
 */
@Composable
fun CertificateDialog(
    state: CertDialogState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Dialog(onDismissRequest = onReject) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                // Title
                Text(
                    text = if (state.isFirstUse) "New Server Certificate" else "Certificate Changed",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.isFirstUse) TextPrimary else ErrorRed,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text =
                        if (state.isFirstUse) {
                            "First connection to this server. Verify the certificate fingerprint matches what the server operator publishes."
                        } else {
                            "The certificate for this server has changed since your last connection. This could indicate a man-in-the-middle attack, or the server operator rotated their certificate."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isFirstUse) TextSecondary else ErrorRed,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Server
                CertField("Server", "${state.certInfo.host}:${state.certInfo.port}")

                // Old fingerprint (cert change only)
                if (!state.isFirstUse && state.oldFingerprint != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CertField("Previous", state.oldFingerprint, isFingerprint = true, color = TextSecondary)
                }

                // New/current fingerprint
                Spacer(modifier = Modifier.height(8.dp))
                CertField(
                    label = if (state.isFirstUse) "Fingerprint" else "New Fingerprint",
                    value = state.certInfo.sha256Fingerprint,
                    isFingerprint = true,
                    color = if (state.isFirstUse) TextPrimary else ErrorRed,
                )

                Spacer(modifier = Modifier.height(8.dp))
                CertField("Subject", state.certInfo.subject)

                Spacer(modifier = Modifier.height(8.dp))
                CertField("Valid Until", state.certInfo.validUntil)

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    ) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = if (state.isFirstUse) BitcoinOrange else ErrorRed,
                            ),
                    ) {
                        Text(if (state.isFirstUse) "Trust" else "Accept New Certificate")
                    }
                }
            }
        }
    }
}

@Composable
private fun CertField(
    label: String,
    value: String,
    isFingerprint: Boolean = false,
    color: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style =
                if (isFingerprint) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                } else {
                    MaterialTheme.typography.bodySmall
                },
            color = color,
        )
    }
}
