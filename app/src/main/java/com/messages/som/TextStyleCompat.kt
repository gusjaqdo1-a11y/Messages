package com.messages.som

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/** Compatibility helper used by the link-highlighting builder. */
private inline fun AnnotatedString.Builder.withStyleCompat(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit
) {
    pushStyle(style)
    block()
    pop()
}

/** Local alias so MainActivity can use withStyle without relying on an extra import. */
inline fun AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: AnnotatedString.Builder.() -> Unit
) {
    withStyleCompat(style, block)
}
