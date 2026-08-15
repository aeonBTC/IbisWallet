package github.aeonbtc.ibiswallet.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Lightweight GitHub-flavored release-notes formatter.
 * Handles the common subset used in release bodies: headings, lists, bold/italic/code, links.
 */
object ReleaseNotesFormatter {
    fun toAnnotatedString(markdown: String): AnnotatedString {
        val normalized =
            markdown
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace(HTML_COMMENT_REGEX, "")
                .trim()
        if (normalized.isEmpty()) return AnnotatedString("")

        val result =
            buildAnnotatedString {
                val lines = normalized.lines()
                var index = 0
                var previousWasBlank = true

                while (index < lines.size) {
                    val rawLine = lines[index]
                    val trimmed = rawLine.trim()

                    when {
                        trimmed.isEmpty() -> {
                            if (!previousWasBlank && index < lines.lastIndex) {
                                append('\n')
                                previousWasBlank = true
                            }
                        }

                        HORIZONTAL_RULE_REGEX.matches(trimmed) -> {
                            if (!previousWasBlank) append('\n')
                            append("────────")
                            append('\n')
                            previousWasBlank = true
                        }

                        trimmed.startsWith("```") -> {
                            if (!previousWasBlank) append('\n')
                            index++
                            val codeLines = mutableListOf<String>()
                            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                                codeLines += lines[index]
                                index++
                            }
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                append(codeLines.joinToString("\n"))
                            }
                            append('\n')
                            previousWasBlank = false
                        }

                        HEADING_REGEX.matchEntire(trimmed) != null -> {
                            val match = HEADING_REGEX.matchEntire(trimmed)!!
                            val text = match.groupValues[2].trim()
                            if (!previousWasBlank) append('\n')
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                appendInlineMarkdown(text)
                            }
                            append('\n')
                            previousWasBlank = false
                        }

                        BULLET_REGEX.matchEntire(trimmed) != null -> {
                            val match = BULLET_REGEX.matchEntire(trimmed)!!
                            val text = match.groupValues[1].trim()
                            append("• ")
                            appendInlineMarkdown(text)
                            append('\n')
                            previousWasBlank = false
                        }

                        NUMBERED_REGEX.matchEntire(trimmed) != null -> {
                            val match = NUMBERED_REGEX.matchEntire(trimmed)!!
                            val number = match.groupValues[1]
                            val text = match.groupValues[2].trim()
                            append("$number. ")
                            appendInlineMarkdown(text)
                            append('\n')
                            previousWasBlank = false
                        }

                        QUOTE_REGEX.matchEntire(trimmed) != null -> {
                            val match = QUOTE_REGEX.matchEntire(trimmed)!!
                            val text = match.groupValues[1].trim()
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append("“")
                                appendInlineMarkdown(text)
                                append("”")
                            }
                            append('\n')
                            previousWasBlank = false
                        }

                        else -> {
                            appendInlineMarkdown(trimmed)
                            append('\n')
                            previousWasBlank = false
                        }
                    }
                    index++
                }
            }

        val text = result.text.trimEnd()
        return if (text.length == result.text.length) {
            result
        } else {
            result.subSequence(0, text.length)
        }
    }

    private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val close = text.indexOf(marker, startIndex = i + 2)
                    if (close > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendInlineMarkdown(text.substring(i + 2, close))
                        }
                        i = close + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("~~", i) -> {
                    val close = text.indexOf("~~", startIndex = i + 2)
                    if (close > i + 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            appendInlineMarkdown(text.substring(i + 2, close))
                        }
                        i = close + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("`", i) -> {
                    val close = text.indexOf('`', startIndex = i + 1)
                    if (close > i + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("[", i) -> {
                    val linkMatch = INLINE_LINK_REGEX.find(text, i)
                    if (linkMatch != null && linkMatch.range.first == i) {
                        append(linkMatch.groupValues[1])
                        i = linkMatch.range.last + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                text.startsWith("*", i) || text.startsWith("_", i) -> {
                    val marker = text[i]
                    val close = text.indexOf(marker, startIndex = i + 1)
                    val validStart = i == 0 || !text[i - 1].isLetterOrDigit()
                    val validEnd = close > i + 1 && (close == text.lastIndex || !text[close + 1].isLetterOrDigit())
                    if (validStart && validEnd) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendInlineMarkdown(text.substring(i + 1, close))
                        }
                        i = close + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }

    private val HTML_COMMENT_REGEX = Regex("<!--[\\s\\S]*?-->")
    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val BULLET_REGEX = Regex("^[-*+]\\s+(.+)$")
    private val NUMBERED_REGEX = Regex("^(\\d+)\\.\\s+(.+)$")
    private val QUOTE_REGEX = Regex("^>\\s?(.*)$")
    private val HORIZONTAL_RULE_REGEX = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")
    private val INLINE_LINK_REGEX = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
}
