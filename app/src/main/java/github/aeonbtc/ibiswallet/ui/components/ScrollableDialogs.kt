package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.aeonbtc.ibiswallet.localization.ProvideLocalizedResources
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import github.aeonbtc.ibiswallet.R

@Composable
fun ScrollableDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    containerColor: Color = DarkSurface,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    bottomSpacing: Dp = 24.dp,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        ProvideLocalizedResources {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .padding(16.dp)
                            .heightIn(max = 720.dp)
                            .then(modifier),
                    shape = shape,
                    color = containerColor,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(contentPadding),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            content()
                        }

                        if (actions != null) {
                            Spacer(modifier = Modifier.height(bottomSpacing))
                            actions()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    containerColor: Color = DarkSurface,
    shape: Shape = RoundedCornerShape(12.dp),
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    ScrollableDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        containerColor = containerColor,
        shape = shape,
        actions = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                confirmButton()
                if (dismissButton != null) {
                    dismissButton()
                }
            }
        },
    ) {
        if (icon != null) {
            icon()
        }

        if (title != null) {
            if (icon != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            title()
        }

        if (text != null) {
            if (title != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            text()
        }
    }
}

@Composable
fun IbisConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    containerColor: Color = DarkSurface,
    shape: Shape = RoundedCornerShape(12.dp),
    message: String? = null,
    dismissText: String? = null,
    showDismissButton: Boolean = true,
    onDismissAction: (() -> Unit)? = onDismissRequest,
    confirmEnabled: Boolean = true,
    confirmColor: Color = BitcoinOrange,
    icon: (@Composable () -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val resolvedDismissText =
        when {
            !showDismissButton -> null
            dismissText != null -> dismissText
            else -> stringResource(R.string.loc_51bac044)
        }
    ScrollableDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        containerColor = containerColor,
        shape = shape,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (resolvedDismissText != null && onDismissAction != null) {
                    IbisButton(
                        onClick = onDismissAction,
                        modifier = Modifier.widthIn(min = 84.dp),
                        activeColor = TextSecondary,
                    ) {
                        Text(
                            text = resolvedDismissText,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Spacer(modifier = Modifier.widthIn(min = 12.dp))
                }

                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.widthIn(min = 84.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = confirmColor,
                            disabledContainerColor = confirmColor.copy(alpha = 0.3f),
                        ),
                ) {
                    Text(
                        text = confirmText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (message != null || body != null) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }

        if (body != null) {
            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            body()
        }
    }
}
