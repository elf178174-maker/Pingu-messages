package app.pingu.messages.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import app.pingu.messages.core.text.TextEntity
import app.pingu.messages.core.text.TextEntityDetector

/**
 * Message text with links, phone numbers, e-mail addresses and street addresses made tappable.
 *
 * Detection is done by [TextEntityDetector] rather than by `Linkify`, so the same rules apply
 * everywhere and are unit tested. Highlight ranges from search are drawn on top of the entity
 * styling, so a match inside a link stays visible.
 */
@Composable
fun LinkedMessageText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    linkColor: Color = color,
    highlightRanges: List<IntRange> = emptyList(),
    highlightBackground: Color = Color.Unspecified,
    onEntityClick: (TextEntity) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val entities = remember(text) { TextEntityDetector.detect(text) }

    val annotated = remember(text, entities, highlightRanges, linkColor, highlightBackground) {
        buildAnnotatedString {
            append(text)
            entities.forEach { entity ->
                addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    entity.start,
                    entity.endExclusive,
                )
                addStringAnnotation(
                    tag = ENTITY_TAG,
                    annotation = entity.start.toString(),
                    start = entity.start,
                    end = entity.endExclusive,
                )
            }
            highlightRanges.forEach { range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(start, text.length)
                if (start < end && highlightBackground != Color.Unspecified) {
                    addStyle(SpanStyle(background = highlightBackground), start, end)
                }
            }
        }
    }

    if (entities.isEmpty()) {
        androidx.compose.material3.Text(
            text = annotated,
            style = style,
            color = color,
            modifier = modifier,
        )
        return
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            val hit = annotated.getStringAnnotations(ENTITY_TAG, offset, offset).firstOrNull()
            val entity = hit?.item?.toIntOrNull()?.let { start -> entities.firstOrNull { it.start == start } }
            if (entity != null) onEntityClick(entity) else onClick()
        },
    )
}

/** Builds a highlighted string for search results, where nothing is tappable inside the row. */
fun highlightedText(
    text: String,
    ranges: List<IntRange>,
    background: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    ranges.forEach { range ->
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (start < end) addStyle(SpanStyle(background = background), start, end)
    }
}

private const val ENTITY_TAG = "entity"
