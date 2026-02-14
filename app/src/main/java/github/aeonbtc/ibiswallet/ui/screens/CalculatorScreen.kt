package github.aeonbtc.ibiswallet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Calculator-themed colors (iOS-style dark calculator)
private val CalcBackground = Color(0xFF000000)
private val CalcDisplayText = Color.White
private val CalcNumberButton = Color(0xFF333333)
private val CalcNumberText = Color.White
private val CalcOperatorButton = Color(0xFFFF9F0A)
private val CalcOperatorText = Color.White
private val CalcFunctionButton = Color(0xFFA5A5A5)
private val CalcFunctionText = Color.Black
private val CalcHighlightBg = Color.White
private val CalcHighlightText = Color(0xFFFF9F0A)

/**
 * Mutable state holder for the calculator. Kept as a stable class so that
 * Compose can correctly track reads/writes without stale-lambda issues.
 */
private class CalcState(
    val verifyCloakCode: (String) -> Boolean,
    val onUnlock: () -> Unit
) {
    var display by mutableStateOf("0")
    var rawInput by mutableStateOf("")
    var firstOperand by mutableStateOf<Double?>(null)
    var pendingOperator by mutableStateOf<String?>(null)
    var resetOnNextDigit by mutableStateOf(false)
    var lastOperand by mutableStateOf<Double?>(null)
    var lastOperator by mutableStateOf<String?>(null)

    fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble() &&
            value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()
        ) {
            "%,d".format(value.toLong())
        } else {
            val formatted = "%.10g".format(value)
            if (formatted.contains('.')) formatted.trimEnd('0').trimEnd('.') else formatted
        }
    }

    private fun applyOp(op: String, a: Double, b: Double): Double = when (op) {
        "+" -> a + b
        "\u2212" -> a - b
        "\u00D7" -> a * b
        "\u00F7" -> if (b != 0.0) a / b else Double.NaN
        else -> b
    }

    fun parseDisplay(): Double = display.replace(",", "").toDoubleOrNull() ?: 0.0

    private fun formatPlain(plain: String): String {
        if (plain.contains(".")) {
            val parts = plain.split(".")
            val intPart = parts[0].toLongOrNull()
            val intFormatted = if (intPart != null) "%,d".format(intPart) else parts[0]
            return "$intFormatted.${parts.getOrElse(1) { "" }}"
        }
        if (plain == "-") return plain
        val num = plain.toLongOrNull()
        return if (num != null) "%,d".format(num) else plain
    }

    fun digit(d: String) {
        // Always accumulate rawInput for secret code detection,
        // even when the display doesn't change (e.g. leading zeros)
        if (resetOnNextDigit) {
            display = d
            rawInput = d
            resetOnNextDigit = false
        } else {
            rawInput += d
            val plain = display.replace(",", "")
            when {
                plain == "0" && d != "." -> {
                    display = d
                }
                d == "." && plain.contains(".") -> return
                plain.replace(".", "").replace("-", "").length >= 12 -> return
                else -> {
                    display = formatPlain(plain + d)
                }
            }
        }
    }

    fun operator(op: String) {
        val cur = parseDisplay()
        if (firstOperand != null && pendingOperator != null && !resetOnNextDigit) {
            val result = applyOp(pendingOperator!!, firstOperand!!, cur)
            display = if (result.isNaN()) "Error" else formatResult(result)
            firstOperand = result
        } else {
            firstOperand = cur
        }
        pendingOperator = op
        resetOnNextDigit = true
        rawInput = ""
        lastOperand = null
        lastOperator = null
    }

    fun equals() {
        if (rawInput.isNotEmpty() && verifyCloakCode(rawInput)) {
            onUnlock()
            return
        }
        if (firstOperand != null && pendingOperator != null) {
            val cur = parseDisplay()
            val result = applyOp(pendingOperator!!, firstOperand!!, cur)
            lastOperand = cur
            lastOperator = pendingOperator
            display = if (result.isNaN()) "Error" else formatResult(result)
            firstOperand = result
            pendingOperator = null
            resetOnNextDigit = true
            rawInput = ""
        } else if (lastOperator != null && lastOperand != null) {
            val cur = parseDisplay()
            val result = applyOp(lastOperator!!, cur, lastOperand!!)
            display = if (result.isNaN()) "Error" else formatResult(result)
            firstOperand = result
            resetOnNextDigit = true
            rawInput = ""
        }
    }

    fun clear() {
        display = "0"
        rawInput = ""
        firstOperand = null
        pendingOperator = null
        resetOnNextDigit = false
        lastOperand = null
        lastOperator = null
    }

    fun toggleSign() {
        val plain = display.replace(",", "")
        if (plain != "0") {
            val newPlain = if (plain.startsWith("-")) plain.drop(1) else "-$plain"
            display = formatPlain(newPlain)
        }
    }

    fun percent() {
        display = formatResult(parseDisplay() / 100.0)
        resetOnNextDigit = true
        rawInput = ""
    }
}

/**
 * A fully functional calculator screen used as the app's cloak mode disguise.
 * Uses basic Box+clickable composables to avoid any Material3 Button issues.
 */
@Composable
fun CalculatorScreen(
    verifyCloakCode: (String) -> Boolean,
    onUnlock: () -> Unit
) {
    val s = remember { CalcState(verifyCloakCode, onUnlock) }

    val fontSize = when {
        s.display.length > 15 -> 32.sp
        s.display.length > 12 -> 38.sp
        s.display.length > 9 -> 48.sp
        else -> 64.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalcBackground)
            .systemBarsPadding()
    ) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = s.display,
                fontSize = fontSize,
                color = CalcDisplayText,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Button grid
        val rows = listOf(
            listOf(
                Btn("AC", CalcFunctionButton, CalcFunctionText) { s.clear() },
                Btn("+/\u2212", CalcFunctionButton, CalcFunctionText) { s.toggleSign() },
                Btn("%", CalcFunctionButton, CalcFunctionText) { s.percent() },
                Btn("\u00F7", CalcOperatorButton, CalcOperatorText,
                    highlight = s.pendingOperator == "\u00F7" && s.resetOnNextDigit) { s.operator("\u00F7") },
            ),
            listOf(
                Btn("7", CalcNumberButton, CalcNumberText) { s.digit("7") },
                Btn("8", CalcNumberButton, CalcNumberText) { s.digit("8") },
                Btn("9", CalcNumberButton, CalcNumberText) { s.digit("9") },
                Btn("\u00D7", CalcOperatorButton, CalcOperatorText,
                    highlight = s.pendingOperator == "\u00D7" && s.resetOnNextDigit) { s.operator("\u00D7") },
            ),
            listOf(
                Btn("4", CalcNumberButton, CalcNumberText) { s.digit("4") },
                Btn("5", CalcNumberButton, CalcNumberText) { s.digit("5") },
                Btn("6", CalcNumberButton, CalcNumberText) { s.digit("6") },
                Btn("\u2212", CalcOperatorButton, CalcOperatorText,
                    highlight = s.pendingOperator == "\u2212" && s.resetOnNextDigit) { s.operator("\u2212") },
            ),
            listOf(
                Btn("1", CalcNumberButton, CalcNumberText) { s.digit("1") },
                Btn("2", CalcNumberButton, CalcNumberText) { s.digit("2") },
                Btn("3", CalcNumberButton, CalcNumberText) { s.digit("3") },
                Btn("+", CalcOperatorButton, CalcOperatorText,
                    highlight = s.pendingOperator == "+" && s.resetOnNextDigit) { s.operator("+") },
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (btn in row) {
                        CalcKey(
                            label = btn.label,
                            bg = if (btn.highlight) CalcHighlightBg else btn.bg,
                            fg = if (btn.highlight) CalcHighlightText else btn.fg,
                            modifier = Modifier.weight(1f),
                            onClick = btn.onClick
                        )
                    }
                }
            }
            // Bottom row: 0 (wide), ., =
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalcKey("0", CalcNumberButton, CalcNumberText, Modifier.weight(2f)) { s.digit("0") }
                CalcKey(".", CalcNumberButton, CalcNumberText, Modifier.weight(1f)) { s.digit(".") }
                CalcKey("=", CalcOperatorButton, CalcOperatorText, Modifier.weight(1f)) { s.equals() }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private data class Btn(
    val label: String,
    val bg: Color,
    val fg: Color,
    val highlight: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun CalcKey(
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 28.sp,
            color = fg,
            textAlign = TextAlign.Center
        )
    }
}
