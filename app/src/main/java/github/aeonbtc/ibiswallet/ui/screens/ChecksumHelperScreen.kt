package github.aeonbtc.ibiswallet.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.components.Bip39SuggestionRow
import github.aeonbtc.ibiswallet.ui.components.SecureDialogSideEffect
import github.aeonbtc.ibiswallet.ui.components.SensitiveSeedIme
import github.aeonbtc.ibiswallet.ui.components.rememberBringIntoViewRequesterOnExpand
import github.aeonbtc.ibiswallet.ui.components.sensitiveSeedKeyboardOptions
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.BorderColor
import github.aeonbtc.ibiswallet.ui.theme.DarkBackground
import github.aeonbtc.ibiswallet.ui.theme.DarkCard
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.ui.theme.ErrorRed
import github.aeonbtc.ibiswallet.ui.theme.SuccessGreen
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import github.aeonbtc.ibiswallet.util.Bip39ChecksumHelper
import github.aeonbtc.ibiswallet.util.QrFormatParser
import github.aeonbtc.ibiswallet.util.SecureClipboard
import github.aeonbtc.ibiswallet.util.generateQrBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.SecureRandom

/**
 * Finds valid BIP39 checksum words for a dice-generated 11- or 23-word prefix.
 * 11 words yield 128 candidates, 23 words yield 8. The user picks one manually,
 * rolls the in-app random picker, or derives the pick from their own dice rolls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChecksumHelperScreen(
    onBack: () -> Unit,
    onUsePhrase: (phrase: String) -> Unit,
) {
    val context = LocalContext.current
    var twelveWordMode by remember { mutableStateOf(true) }
    val expectedCount = if (twelveWordMode) 11 else 23
    var inputField by remember { mutableStateOf(TextFieldValue("")) }
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var diceText by remember { mutableStateOf("") }
    var showDice by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val wordlist = remember { QrFormatParser.getWordlist(context) }
    val wordSet = remember(wordlist) { wordlist.toSet() }
    val tokens =
        remember(inputField.text) {
            inputField.text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        }
    val unknownWord = remember(tokens) { tokens.firstOrNull { it !in wordSet } }
    val readyToCompute = tokens.size == expectedCount && unknownWord == null

    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(inputField.text, twelveWordMode) {
        selectedWord = null
        candidates =
            if (readyToCompute) {
                withContext(Dispatchers.Default) {
                    Bip39ChecksumHelper.checksumCandidates(tokens, wordlist)
                }
            } else {
                emptyList()
            }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(3000)
            copied = false
        }
    }

    val fullPhrase =
        remember(tokens, selectedWord) {
            selectedWord?.let { (tokens + it).joinToString(" ") }
        }
    val phraseRequester = rememberBringIntoViewRequesterOnExpand(fullPhrase != null, fullPhrase)
    LaunchedEffect(showQrDialog, fullPhrase) {
        qrBitmap = null
        if (showQrDialog && fullPhrase != null) {
            qrBitmap =
                withContext(Dispatchers.Default) {
                    generateQrBitmap(fullPhrase)
                }
        }
    }
    val diceIndex =
        remember(diceText, candidates) {
            if (diceText.isBlank() || candidates.isEmpty()) {
                null
            } else {
                Bip39ChecksumHelper.diceRollsToIndex(diceText, candidates.size)
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
                text = stringResource(R.string.loc_7f2c91aa),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Text(
            text = stringResource(R.string.loc_3d8b44f1),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChecksumModeButton(
                label = stringResource(R.string.loc_51f0b823),
                isSelected = twelveWordMode,
                onClick = {
                    twelveWordMode = true
                    inputField = TextFieldValue("")
                    diceText = ""
                },
                modifier = Modifier.weight(1f),
            )
            ChecksumModeButton(
                label = stringResource(R.string.loc_62d4e917),
                isSelected = !twelveWordMode,
                onClick = {
                    twelveWordMode = false
                    inputField = TextFieldValue("")
                    diceText = ""
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_c42d17b5),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.import_seed_words_entered,
                                tokens.size,
                                tokens.size,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                SensitiveSeedIme {
                    OutlinedTextField(
                        value = inputField,
                        onValueChange = { input ->
                            val normalized =
                                QrFormatParser.expandAbbreviatedMnemonic(
                                    context,
                                    input.text.lowercase(),
                                )
                            inputField =
                                input.copy(
                                    text = normalized,
                                    selection =
                                        TextRange(
                                            input.selection.end.coerceAtMost(normalized.length),
                                        ),
                                )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                        shape = RoundedCornerShape(8.dp),
                        placeholder = {
                            Text(
                                stringResource(R.string.loc_08c3f5a2),
                                color = TextSecondary.copy(alpha = 0.5f),
                            )
                        },
                        keyboardOptions = sensitiveSeedKeyboardOptions(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BitcoinOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                cursorColor = BitcoinOrange,
                            ),
                    )
                }
                Bip39SuggestionRow(
                    input = inputField.text,
                    wordlist = wordlist,
                    onWordSelected = { completedInput ->
                        inputField =
                            TextFieldValue(
                                text = completedInput,
                                selection = TextRange(completedInput.length),
                            )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (unknownWord != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.loc_93d6a8b2, unknownWord),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (readyToCompute) {
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
                    Text(
                        text = stringResource(R.string.loc_40e6c1b9, candidates.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = SuccessGreen,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.loc_2b7d93e0),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        candidates.forEach { word ->
                            val isSelected = word == selectedWord
                            Box(
                                modifier =
                                    Modifier
                                        .border(
                                            border = BorderStroke(1.dp, if (isSelected) BitcoinOrange else BorderColor),
                                            shape = RoundedCornerShape(8.dp),
                                        ).background(
                                            color = if (isSelected) BitcoinOrange else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                        ).clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedWord = word },
                            ) {
                                Text(
                                    text = word,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) DarkBackground else BitcoinOrange,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

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
                    Button(
                        onClick = {
                            selectedWord = candidates[SecureRandom().nextInt(candidates.size)]
                        },
                        enabled = candidates.isNotEmpty(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text =
                                stringResource(
                                    if (selectedWord == null) {
                                        R.string.loc_6e91a045
                                    } else {
                                        R.string.loc_1c4f82d7
                                    },
                                ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BorderColor,
                        )
                        Text(
                            text = stringResource(R.string.loc_1db77587),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = BorderColor,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDice = !showDice }
                                .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.loc_d5a03768),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Icon(
                            imageVector =
                                if (showDice) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                    AnimatedVisibility(
                        visible = showDice,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.loc_19e4a0d3),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = diceText,
                                onValueChange = { diceText = it.filter { char -> char in '0'..'9' } },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.loc_77b2e4f3),
                                        color = TextSecondary.copy(alpha = 0.5f),
                                    )
                                },
                                singleLine = true,
                                isError = diceText.isNotBlank() && diceIndex == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BitcoinOrange,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        cursorColor = BitcoinOrange,
                                    ),
                            )
                            if (diceText.isNotBlank() && diceIndex == null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.loc_f04c98ab),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                )
                            }
                            if (diceIndex != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = candidates[diceIndex],
                                        style = MaterialTheme.typography.titleMedium,
                                        color = BitcoinOrange,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = {
                                            selectedWord = candidates[diceIndex]
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = BitcoinOrange,
                                                disabledContainerColor = BitcoinOrange.copy(alpha = 0.3f),
                                            ),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.loc_acfc8aab),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            fullPhrase?.let { phrase ->
                if (showQrDialog) {
                    SeedQrDialog(
                        phrase = phrase,
                        qrBitmap = qrBitmap,
                        copied = copied,
                        onCopy = {
                            SecureClipboard.copyAndScheduleClear(context, phrase)
                            copied = true
                        },
                        onDismiss = {
                            showQrDialog = false
                            copied = false
                        },
                    )
                }
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(phraseRequester),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.loc_5a83d2c6),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = phrase,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showQrDialog = true },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = DarkSurfaceVariant,
                                        contentColor = TextSecondary,
                                    ),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.loc_8d2f5b10),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Button(
                                onClick = { onUsePhrase(phrase) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = BitcoinOrange,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.loc_b9e614f0),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            Text(
                text = stringResource(R.string.loc_e1c7405d),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChecksumModeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (isSelected) BitcoinOrange else DarkSurfaceVariant
    val contentColor = if (isSelected) DarkBackground else TextSecondary
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, if (isSelected) BitcoinOrange else BorderColor),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SeedQrDialog(
    phrase: String,
    qrBitmap: Bitmap?,
    copied: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        SecureDialogSideEffect()
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkCard,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.loc_5a83d2c6),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.loc_d2c0aec0),
                            tint = TextSecondary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.loc_6e2afb3f),
                            modifier =
                                Modifier
                                    .size(184.dp)
                                    .padding(8.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            color = BitcoinOrange,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.key_material_scan_qr_format,
                            stringResource(R.string.loc_24d8d452),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCopy,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = BitcoinOrange,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            stringResource(
                                if (copied) {
                                    R.string.loc_ad35e265
                                } else {
                                    R.string.loc_ed8814bc
                                },
                            ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.loc_d2c0aec0),
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
