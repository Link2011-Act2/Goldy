package jp.linkserver.goldy.compose

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import org.commonmark.node.Image
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin

/** Core + tables only: no HTML, image loader, linkify or implicit Activity launching. */
@Composable
fun ReleaseNotes(
    markdown: String,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "No release notes were provided.",
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val open by rememberUpdatedState(onOpenUrl)
    val colors = MaterialTheme.colorScheme
    val foreground = colors.onSurface.toArgb()
    val link = colors.primary.toArgb()
    val code = colors.surfaceContainerHighest.toArgb()
    val density = LocalDensity.current
    val family = LocalFontFamilyResolver.current.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal,
        style.fontStyle ?: FontStyle.Normal, style.fontSynthesis ?: androidx.compose.ui.text.font.FontSynthesis.All)
    val textPx = with(density) { (if (style.fontSize.isSp) style.fontSize else 14.sp).toPx() }
    val markwon = remember(context, foreground, link, code) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, url -> if (safeWebUrl(url)) open(url) }
                }
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    // Retain image alt text only; never create drawable spans or HTML spans.
                    builder.on(Image::class.java) { visitor, node -> visitor.visitChildren(node) }
                    builder.on(HtmlBlock::class.java) { _, _ -> }
                    builder.on(HtmlInline::class.java) { _, _ -> }
                }
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(link).codeTextColor(foreground).codeBackgroundColor(code)
                        .blockQuoteColor(link)
                }
            }).build()
    }
    val content = markdown.ifBlank { emptyText }
    val rendered = remember(markwon, content) { markwon.toMarkdown(content) }
    AndroidView(
        modifier = modifier,
        factory = { TextView(it).apply {
            setTextIsSelectable(true)
            movementMethod = LinkMovementMethod.getInstance()
            // Android state must not overwrite the latest release content after recreation.
            isSaveEnabled = false
        } },
        update = { view ->
            view.setTextColor(foreground)
            view.setLinkTextColor(link)
            view.typeface = family.value as Typeface
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
            if (style.lineHeight.isSp) {
                val height = with(density) { style.lineHeight.toPx() }
                view.setLineSpacing((height - view.paint.fontSpacing).coerceAtLeast(0f), 1f)
            } else view.setLineSpacing(0f, 1f)
            if (view.tag !== rendered) {
                markwon.setParsedMarkdown(view, rendered)
                view.tag = rendered
            }
        }
    )
}
