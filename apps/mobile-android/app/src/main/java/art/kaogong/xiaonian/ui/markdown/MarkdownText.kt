package art.kaogong.xiaonian.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import art.kaogong.xiaonian.ui.theme.LocalFontScale
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 轻量 Markdown 渲染。
 *
 * 覆盖模型实际会输出的那几种：代码块、行内代码、粗体、斜体、标题、
 * 无序/有序列表、分隔线、链接。不引第三方库——这类库通常自带一整套
 * 主题与排版约定，改起来比手写这 200 行更费。
 *
 * 流式场景下输入可能是半截的（比如 ``` 还没闭合），解析必须容错。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = XiaonianColors.Ink,
    baseStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }

    // 字号倍率从设置里来；只在非 1.0 时重算样式，避免无谓的对象分配
    val scale = LocalFontScale.current
    val body = if (scale == 1f) {
        baseStyle
    } else {
        baseStyle.copy(
            fontSize = baseStyle.fontSize * scale,
            lineHeight = baseStyle.lineHeight * scale,
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Code -> CodeBlock(code = block.code, language = block.language)
                is MdBlock.Heading -> Text(
                    text = renderInline(block.text, color),
                    style = body.copy(
                        fontSize = when (block.level) {
                            1 -> 22.sp
                            2 -> 19.sp
                            else -> 17.sp
                        } * scale,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp * scale,
                    ),
                    color = color,
                )
                is MdBlock.Bullet -> BulletRow(block, color, body)
                is MdBlock.Ordered -> OrderedRow(block, color, body)
                is MdBlock.Rule -> HorizontalDivider(
                    color = XiaonianColors.Divider,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                is MdBlock.Table -> TableBlock(block)
                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text, color),
                    style = body,
                    color = color,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 解析

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String, val level: Int = 0) : MdBlock
    data class Ordered(val number: String, val text: String, val level: Int = 0) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^[-*+]\\s+(.*)$")
private val ORDERED = Regex("^(\\d{1,3})[.)]\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])\\1{2,}\\s*$")
/** 表格分隔行：|---|---| 或 |:--|--:| */
private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun splitRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

internal fun parseBlocks(markdown: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = markdown.replace("\r\n", "\n").split("\n")

    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            out += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // 代码块：碰到 ``` 就开始收，直到下一个 ``` 或文本结束（流式可能没闭合）
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            val language = line.trim().removePrefix("```").trim().ifEmpty { null }
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            if (i < lines.size) i++ // 跳过收尾的 ```
            out += MdBlock.Code(language, code.toString().trimEnd())
            continue
        }

        if (line.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        // 表格：表头行 + 分隔行才算，避免把普通 | 文本误判成表格
        if (line.trimStart().startsWith("|") && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1])) {
            flushParagraph()
            val header = splitRow(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                rows += splitRow(lines[i])
                i++
            }
            out += MdBlock.Table(header, rows)
            continue
        }

        if (RULE.matches(line)) {
            flushParagraph()
            out += MdBlock.Rule
            i++
            continue
        }

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            flushParagraph()
            out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        // 列表按缩进分档，每两空格一层，最多四层
        val indent = line.length - line.trimStart().length
        val level = (indent / 2).coerceIn(0, 4)
        val trimmed = line.trimStart()

        val bullet = BULLET.matchEntire(trimmed)
        if (bullet != null) {
            flushParagraph()
            out += MdBlock.Bullet(bullet.groupValues[1].trim(), level)
            i++
            continue
        }

        val ordered = ORDERED.matchEntire(trimmed)
        if (ordered != null) {
            flushParagraph()
            out += MdBlock.Ordered(ordered.groupValues[1], ordered.groupValues[2].trim(), level)
            i++
            continue
        }

        // 普通行：并进当前段落，保留换行
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(line.trim())
        i++
    }

    flushParagraph()
    return out
}

// ---------------------------------------------------------------- 行内样式

private val codeBg = Color(0xFFF0F0F2)

/** 逐字符扫描，避免正则替换后位置错乱 */
internal fun renderInline(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg,
                            fontSize = 14.sp,
                        ),
                    )
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }

            text[i] == '[' -> {
                // [label](url)
                val close = text.indexOf(']', i + 1)
                if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                    val paren = text.indexOf(')', close + 2)
                    if (paren > 0) {
                        pushStyle(
                            SpanStyle(
                                color = color,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        append(text.substring(i + 1, close))
                        pop()
                        i = paren + 1
                        continue
                    }
                }
                append(text[i]); i++
            }

            text[i] == '*' && i + 1 < text.length && text[i + 1] != '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }

            else -> {
                append(text[i]); i++
            }
        }
    }
}

// ---------------------------------------------------------------- 块渲染

@Composable
private fun CodeBlock(language: String?, code: String) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF6F6F7))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = XiaonianColors.InkTertiary,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            ),
            color = XiaonianColors.Ink,
            modifier = Modifier.horizontalScroll(scroll),
        )
    }
}

/** 表格：等宽列 + 表头底色。Compose 没有内置表格，手搭最省事 */
@Composable
private fun TableBlock(block: MdBlock.Table) {
    val columns = block.header.size.coerceAtLeast(1)
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, XiaonianColors.Divider, shape),
    ) {
        Row(modifier = Modifier.background(Color(0xFFF7F7F8))) {
            block.header.forEach { cell ->
                Text(
                    // 单元格里同样会有 **粗体** / `代码`，必须走行内解析
                    text = renderInline(cell, XiaonianColors.Ink),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = XiaonianColors.Ink,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        HorizontalDivider(color = XiaonianColors.Divider)

        block.rows.forEachIndexed { index, row ->
            Row {
                for (column in 0 until columns) {
                    Text(
                        text = renderInline(row.getOrNull(column).orEmpty(), XiaonianColors.Ink),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaonianColors.Ink,
                    )
                }
            }
            if (index < block.rows.lastIndex) {
                HorizontalDivider(color = XiaonianColors.Divider.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun BulletRow(block: MdBlock.Bullet, color: Color, baseStyle: TextStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.level * 16).dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(XiaonianColors.InkSecondary),
        )
        Spacer(Modifier.width(9.dp))
        Text(text = renderInline(block.text, color), style = baseStyle, color = color)
    }
}

@Composable
private fun OrderedRow(block: MdBlock.Ordered, color: Color, baseStyle: TextStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.level * 16).dp),
    ) {
        Text(
            text = "${block.number}.",
            style = baseStyle,
            color = XiaonianColors.InkSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = renderInline(block.text, color), style = baseStyle, color = color)
    }
}
