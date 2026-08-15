package github.aeonbtc.ibiswallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.localization.ProvideLocalizedResources
import github.aeonbtc.ibiswallet.ui.theme.BitcoinOrange
import github.aeonbtc.ibiswallet.ui.theme.DarkSurface
import github.aeonbtc.ibiswallet.ui.theme.TextSecondary

@Composable
fun ScrollableDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    containerColor: Color = DarkSurface,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    bottomSpacing: Dp = 24.dp,
    maxWidth: Dp = 560.dp,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.decorView.filterTouchesWhenObscured = true
            val activityFlags = (view.context as? android.app.Activity)?.window?.attributes?.flags ?: 0
            if (activityFlags and WindowManager.LayoutParams.FLAG_SECURE != 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        ProvideLocalizedResources {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxWidth)
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
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
    confirmColor: Color = BitcoinOrange,
    maxWidth: Dp = 560.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    bottomSpacing: Dp = 16.dp,
    actionHeight: Dp = 40.dp,
    titleStyle: TextStyle? = null,
    messageStyle: TextStyle? = null,
    actionTextStyle: TextStyle? = null,
    icon: (@Composable () -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val resolvedDismissText =
        when {
            !showDismissButton -> null
            dismissText != null -> dismissText
            else -> stringResource(R.string.loc_51bac044)
        }
    val resolvedTitleStyle = titleStyle ?: MaterialTheme.typography.titleMedium
    val resolvedMessageStyle = messageStyle ?: MaterialTheme.typography.bodyMedium
    val resolvedActionTextStyle = actionTextStyle ?: MaterialTheme.typography.bodyMedium
    ScrollableDialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        containerColor = containerColor,
        shape = shape,
        maxWidth = maxWidth,
        contentPadding = contentPadding,
        bottomSpacing = bottomSpacing,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (resolvedDismissText != null && onDismissAction != null) {
                    IbisButton(
                        onClick = onDismissAction,
                        modifier = Modifier.widthIn(min = 96.dp).height(actionHeight),
                        enabled = dismissEnabled,
                        activeColor = TextSecondary,
                    ) {
                        Text(
                            text = resolvedDismissText,
                            style = resolvedActionTextStyle,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(modifier = Modifier.widthIn(min = 10.dp))
                }

                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.widthIn(min = 96.dp).height(actionHeight),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = confirmColor,
                            disabledContainerColor = confirmColor.copy(alpha = 0.3f),
                        ),
                ) {
                    Text(
                        text = confirmText,
                        style = resolvedActionTextStyle,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = title,
            style = resolvedTitleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (message != null || body != null) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (message != null) {
            Text(
                text = message,
                style = resolvedMessageStyle,
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
