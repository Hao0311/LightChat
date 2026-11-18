package app.lightchat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Block {
    data class Para(val text: String) : Block
    data class Head(val level: Int, val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
    data class Bullets(val items: List<String>) : Block
}

fun parseMarkdown(src: String): List<Block> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = ArrayList<Block>()
    val para = StringBuilder()
    val bullets = ArrayList<String>()

    fun flushPara() {
        if (para.isNotBlank()) out.add(Block.Para(para.toString().trim()))
        para.setLength(0)
    }

    fun flushBullets() {
        if (bullets.isNotEmpty()) out.add(Block.Bullets(bullets.toList()))
        bullets.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flushPara(); flushBullets()
                val lang = trimmed.removePrefix("```").trim()
                val buf = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    buf.append(lines[i]).append('\n')
                    i++
                }
                out.add(Block.Code(lang, buf.toString().trimEnd('\n')))
                i++
                continue
            }
            trimmed.isBlank() -> { flushPara(); flushBullets() }
            trimmed.startsWith("#") -> {
                flushPara(); flushBullets()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
                out.add(Block.Head(level, trimmed.drop(level).trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushPara()
                bullets.add(trimmed.drop(2).trim())
            }
            Regex("^\\d+[.)]\\s+").containsMatchIn(trimmed) -> {
                flushPara()
                bullets.add(trimmed.replaceFirst(Regex("^\\d+[.)]\\s+"), "").trim())
            }
            else -> {
                flushBullets()
                if (para.isNotEmpty()) para.append('\n')
                para.append(trimmed)
            }
        }
        i++
    }
    flushPara(); flushBullets()
    return out
}

private val INLINE_CODE = Regex("`([^`]+)`")
private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")

fun inlineAnnotated(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var idx = 0
    val marks = ArrayList<Triple<Int, Int, String>>() // start, end, kind
    INLINE_CODE.findAll(text).forEach { marks.add(Triple(it.range.first, it.range.last + 1, "c")) }
    BOLD.findAll(text).forEach { m ->
        if (marks.none { it.first <= m.range.first && m.range.last + 1 <= it.second }) {
            marks.add(Triple(m.range.first, m.range.last + 1, "b"))
        }
    }
    marks.sortBy { it.first }
    for ((start, end, kind) in marks) {
        if (start < idx) continue
        append(text.substring(idx, start))
        val body = text.substring(start, end)
        when (kind) {
            "c" -> {
                val inner = body.trim('`')
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF1F3F6)))
                append(inner)
                pop()
            }
            else -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                append(body.trim('*'))
                pop()
            }
        }
        idx = end
    }
    if (idx < text.length) append(text.substring(idx))
}

private val CODE_TOKEN = Regex(
    "(#[^\\n]*|//[^\\n]*)|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')|" +
        "\\b(def|class|return|if|elif|else|for|while|import|from|const|let|var|function|new|try|catch|finally|" +
        "throw|async|await|true|false|null|None|True|False|self|print|in|not|and|or|public|private|protected|" +
        "static|void|int|long|float|double|String|fun|val|when|object|package|interface|type|struct|func|" +
        "namespace|using|export|default|extends|implements|this|super|yield)\\b|(\\b\\d+(?:\\.\\d+)?\\b)"
)

private fun highlightCode(code: String): AnnotatedString = buildAnnotatedString {
    var idx = 0
    CODE_TOKEN.findAll(code).forEach { m ->
        append(code.substring(idx, m.range.first))
        val color = when {
            m.groups[1] != null -> Color(0xFF9199A6)
            m.groups[2] != null -> Color(0xFF3F8F4F)
            m.groups[3] != null -> Color(0xFFA626A4)
            else -> Color(0xFF986801)
        }
        pushStyle(SpanStyle(color = color))
        append(m.value)
        pop()
        idx = m.range.last + 1
    }
    if (idx < code.length) append(code.substring(idx))
}

@Composable
fun MarkdownView(text: String, accent: Color = Color(0xFF4D6BFE)) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(Modifier.fillMaxWidth()) {
        blocks.forEach { b ->
            when (b) {
                is Block.Head -> Text(
                    text = b.text,
                    fontSize = when (b.level) { 1 -> 20.sp; 2 -> 18.sp; 3 -> 16.sp; else -> 15.sp },
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF11161D),
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                is Block.Para -> Text(
                    text = inlineAnnotated(b.text, accent),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Color(0xFF11161D),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
                is Block.Bullets -> Column(Modifier.padding(vertical = 2.dp)) {
                    b.items.forEach { item ->
                        Row(Modifier.padding(vertical = 1.dp)) {
                            Text("•", fontSize = 15.sp, color = Color(0xFF8A93A0), modifier = Modifier.width(16.dp))
                            Text(
                                text = inlineAnnotated(item, accent),
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                color = Color(0xFF11161D),
                            )
                        }
                    }
                }
                is Block.Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    if (b.lang.isNotBlank()) {
                        Text(
                            b.lang,
                            fontSize = 11.sp,
                            color = Color(0xFF8A93A0),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Text(
                        text = highlightCode(b.code),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = Color(0xFF1F2430),
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
