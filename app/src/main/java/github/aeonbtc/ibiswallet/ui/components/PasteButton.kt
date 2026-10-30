package github.aeonbtc.ibiswallet.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.aeonbtc.ibiswallet.R
import github.aeonbtc.ibiswallet.ui.theme.DarkSurfaceVariant
import github.aeonbtc.ibiswallet.util.SecureClipboard

/** Read the clipboard and hand it to [onPaste]; toast when it is empty. Never log the text. */
private fun readClipboardOrToast(
    context: Context,
    emptyMessage: String,
    onPaste: (String) -> Unit,
) {
    val text = SecureClipboard.readText(context)
    if (text != null) {
        onPaste(text)
    } else {
        Toast.makeText(context, emptyMessage, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Compact paste + scan pair for input fields. Paste sits left of the scan icon.
 *
 * Rendered as plain clickable icons rather than `IconButton`, whose 48dp minimum
 * touch target per icon crowds single-line fields. The pair keeps maximum room for
 * typed text and both controls share one [tint] so they follow the screen's rail
 * accent (orange L1, teal Liquid, purple Spark, etc.).
 *
 * [onPaste] receives the trimmed clipboard text and must run the same logic as the
 * field's typed input. Never log the clipboard text.
 */
@Composable
fun ScanPasteIcons(
    onScan: () -> Unit,
    onPaste: (String) -> Unit,
    tint: Color,
    scanContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 22.dp,
    scanIcon: ImageVector = Icons.Default.QrCodeScanner,
) {
    val context = LocalContext.current
    val emptyMessage = stringResource(R.string.paste_clipboard_empty)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Keep the pair off the field's trailing border; IconButton used to supply
        // this inset through its own padding.
        modifier = modifier.padding(end = 8.dp),
    ) {
        FieldActionIcon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = stringResource(R.string.paste_cd),
            tint = tint,
            iconSize = iconSize,
            enabled = enabled,
        ) {
            readClipboardOrToast(context, emptyMessage, onPaste)
        }
        FieldActionIcon(
            imageVector = scanIcon,
            contentDescription = scanContentDescription,
            tint = tint,
            iconSize = iconSize,
            enabled = enabled,
            onClick = onScan,
        )
    }
}

/** Small square icon button shared by the paste/scan pair. */
@Composable
private fun FieldActionIcon(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    iconSize: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(2.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Tile variant for multi-line fields where the QR control is a rounded tile pinned to the
 * bottom-end corner. Mirrors that tile's size, corner radius and background so the two
 * read as a pair.
 */
@Composable
fun PasteTileButton(
    onPaste: (String) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    tileSize: Dp = 40.dp,
    cornerRadius: Dp = 8.dp,
    withBackground: Boolean = true,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val emptyMessage = stringResource(R.string.paste_clipboard_empty)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(tileSize)
                .clip(shape)
                .then(if (withBackground) Modifier.background(DarkSurfaceVariant) else Modifier)
                .clickable(enabled = enabled) {
                    readClipboardOrToast(context, emptyMessage, onPaste)
                },
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = stringResource(R.string.paste_cd),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
