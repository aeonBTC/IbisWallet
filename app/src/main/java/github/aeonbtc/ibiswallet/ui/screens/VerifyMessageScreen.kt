package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.components.IbisButton
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.util.Bip137MessageSigner

private sealed interface VerifyResult {
    data object Valid : VerifyResult

    data object Invalid : VerifyResult
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyMessageScreen(
    onBack: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<VerifyResult?>(null) }

    val canVerify = address.isNotBlank() && message.isNotBlank() && signature.isNotBlank()

    fun verify() {
        result =
            if (Bip137MessageSigner.verify(address, message, signature)) {
                VerifyResult.Valid
            } else {
                VerifyResult.Invalid
            }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.loc_cdfc6e09),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.tools_verify_message_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                ToolField(
                    value = address,
                    onValueChange = {
                        address = it
                        result = null
                    },
                    title = stringResource(R.string.tools_field_address),
                    placeholder = stringResource(R.string.tools_field_address_placeholder),
                )

                Spacer(modifier = Modifier.height(12.dp))

                ToolField(
                    value = message,
                    onValueChange = {
                        message = it
                        result = null
                    },
                    title = stringResource(R.string.tools_field_message),
                    placeholder = stringResource(R.string.tools_field_message_placeholder),
                    singleLine = false,
                    minHeight = 96,
                )

                Spacer(modifier = Modifier.height(12.dp))

                ToolField(
                    value = signature,
                    onValueChange = {
                        signature = it
                        result = null
                    },
                    title = stringResource(R.string.tools_field_signature),
                    placeholder = stringResource(R.string.tools_field_signature_placeholder),
                    monospace = true,
                    singleLine = false,
                    minHeight = 96,
                )

                Spacer(modifier = Modifier.height(16.dp))

                IbisButton(
                    onClick = { verify() },
                    enabled = canVerify,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tools_verify_button),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (result) {
            VerifyResult.Valid -> {
                ToolResultBanner(
                    text = stringResource(R.string.tools_result_valid),
                    color = SuccessGreen,
                )
            }
            VerifyResult.Invalid -> {
                ToolResultBanner(
                    text = stringResource(R.string.tools_result_invalid),
                    color = ErrorRed,
                )
            }
            null -> Unit
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
