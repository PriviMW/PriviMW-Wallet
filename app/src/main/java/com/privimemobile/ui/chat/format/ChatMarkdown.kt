package com.privimemobile.ui.chat.format

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.privimemobile.ui.theme.C

private val URL_PATTERN = java.util.regex.Pattern.compile("https?://[^\\s<\\]]{2,}|(\\bwww\\.[^\\s<\\]]{2,})")

/** Parse simple markdown: **bold**, *italic*, `code`, ~~strikethrough~~ */
fun parseMarkdown(text: String): AnnotatedString {
    val urlMatches = URL_PATTERN.matcher(text)
    val urlRanges = mutableListOf<Triple<Int, Int, String>>()
    while (urlMatches.find()) {
        val match = urlMatches.group()
        val displayUrl = if (match.startsWith("www.")) "https://$match" else match
        urlRanges.add(Triple(urlMatches.start(), match.length, displayUrl))
    }

    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val urlRange = urlRanges.find { it.first == i }
            if (urlRange != null) {
                pushStringAnnotation(tag = "url", annotation = urlRange.third)
                withStyle(SpanStyle(color = C.accent, textDecoration = TextDecoration.Underline)) {
                    append(urlRange.third)
                }
                pop()
                i += urlRange.second
                continue
            }
            when {
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                i + 1 < text.length && text[i] == '~' && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x20FFFFFF),
                            ),
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '@' && (i == 0 || text[i - 1] == ' ' || text[i - 1] == '\n') -> {
                    var j = i + 1
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    if (j > i + 1) {
                        val handle = text.substring(i + 1, j)
                        pushStringAnnotation(tag = "mention", annotation = handle)
                        withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.SemiBold)) {
                            append(text.substring(i, j))
                        }
                        pop()
                        i = j
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
}
