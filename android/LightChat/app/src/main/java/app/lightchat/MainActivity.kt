package app.lightchat

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Accent = Color(0xFF4D6BFE)
private val TextMain = Color(0xFF11161D)
private val TextMuted = Color(0xFF8A93A0)
private val PageBg = Color(0xFFF7F8FA)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = TextMain,
    surface = Color.White,
    onSurface = TextMain,
    surfaceVariant = Color(0xFFF3F5F8),
    outline = Color(0xFFE3E7ED),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

private sealed interface Screen {
    data object List : Screen
    data class Chat(val conv: Conversation) : Screen
    data class Persona(val conv: Conversation) : Screen
    data object Settings : Screen
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(Store.loadSettings(ctx)) }
    val convs = remember {
        Store.loadConversations(ctx).sortedByDescending { it.updatedAt }.toMutableStateList()
    }
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    fun persist() = Store.saveConversations(ctx, convs)

    MaterialTheme(colorScheme = LightColors) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            when (val s = screen) {
                is Screen.List -> ListScreen(
                    convs = convs,
                    personaMode = settings.personaMode,
                    onOpen = { screen = Screen.Chat(it) },
                    onNew = {
                        val c = Conversation().also {
                            it.thinking = settings.thinking
                            it.webSearch = settings.webSearch
                        }
                        convs.add(0, c)
                        persist()
                        screen = Screen.Chat(c)
                    },
                    onSettings = { screen = Screen.Settings },
                )
                is Screen.Chat -> ChatScreen(
                    conv = s.conv,
                    settings = settings,
                    onBack = { persist(); screen = Screen.List },
                    onDelete = { convs.remove(s.conv); persist(); screen = Screen.List },
                    onChanged = { persist() },
                    onOpenPersona = { screen = Screen.Persona(s.conv) },
                )
                is Screen.Persona -> PersonaScreen(
                    conv = s.conv,
                    settings = settings,
                    onBack = { persist(); screen = Screen.Chat(s.conv) },
                )
                is Screen.Settings -> SettingsScreen(
                    initial = settings,
                    onBack = { screen = Screen.List },
                    onSave = {
                        settings = it
                        Store.saveSettings(ctx, it)
                        screen = Screen.List
                    },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ 会话列表 */

@Composable
private fun ListScreen(
    convs: SnapshotStateList<Conversation>,
    personaMode: Boolean,
    onOpen: (Conversation) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("轻聊", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                if (personaMode) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .background(Color(0xFFEFF3FF), RoundedCornerShape(9.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) { Text("人设模式", color = Accent, fontSize = 10.sp) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSettings) { Text("设置", color = TextMuted, fontSize = 14.sp) }
            }

            if (convs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有对话", color = TextMuted, fontSize = 15.sp)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onNew,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.height(44.dp),
                        ) { Text("开始新对话", fontSize = 15.sp) }
                        Spacer(Modifier.height(10.dp))
                        Text("也可以点右下角的 ＋", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(convs, key = { it.id }) { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(c) }
                                .padding(vertical = 14.dp),
                        ) {
                            Text(
                                c.title.ifBlank { "新对话" },
                                fontSize = 16.sp,
                                color = TextMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${c.messages.count { it.role == "user" }} 轮 · ${timeLabel(c.updatedAt)}",
                                fontSize = 12.sp,
                                color = TextMuted,
                            )
                        }
                        HorizontalDivider(color = Color(0xFFF0F2F5))
                    }
                }
            }
        }

        // 悬浮的「新对话」按钮：叠在内容上，不占布局高度
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp)
                .size(56.dp)
                .background(Accent, CircleShape)
                .clickable { onNew() },
            contentAlignment = Alignment.Center,
        ) { Text("＋", color = Color.White, fontSize = 26.sp) }
    }
}

/* ------------------------------------------------------------------ 聊天页 */

@Composable
private fun ChatScreen(
    conv: Conversation,
    settings: Settings,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onChanged: () -> Unit,
    onOpenPersona: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val compat = settings.isCompat()
    // 人设模式总开关：关掉时下面的设定入口、注入、分句渲染全部不生效
    val personaOn = settings.personaMode
    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf(conv.title) }
    var pendingExport by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 待发送的附件（纯文本读入）
    val attachments = remember { mutableStateListOf<Attachment>() }
    var attachNote by remember { mutableStateOf("") }
    // 历史超预算时的提示
    var trimNote by remember { mutableStateOf("") }
    // 正在联网搜索
    var searching by remember { mutableStateOf("") }
    // 本轮生成开始吐字的时间点（算实时速度）
    var genFrom by remember { mutableStateOf(0L) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        attachNote = ""
        uris.forEach { uri ->
            val a = runCatching { readAttachment(ctx, uri) }.getOrNull()
            if (a == null) attachNote = "有一个文件读不出来，已跳过"
            else if (attachments.none { it.name == a.name && it.text == a.text }) attachments.add(a)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingExport.toByteArray()) }
            }
        }
    }

    LaunchedEffect(conv.messages.size, conv.messages.lastOrNull()?.content?.length) {
        if (conv.messages.isNotEmpty()) listState.scrollToItem(conv.messages.lastIndex)
    }

    fun send() {
        val typed = input.trim()
        if ((typed.isEmpty() && attachments.isEmpty()) || streaming) return
        val text = buildUserContent(typed, attachments.toList())
        val titleFrom = typed.ifBlank { attachments.firstOrNull()?.name.orEmpty() }
        conv.messages.add(Msg("user", text))
        conv.messages.add(Msg("assistant", ""))
        val aiIndex = conv.messages.lastIndex
        if (conv.title == "新对话" || conv.title.isBlank()) {
            conv.title = titleFrom.take(18).ifBlank { "新对话" }
        }
        conv.updatedAt = System.currentTimeMillis()
        input = ""
        attachments.clear()
        attachNote = ""
        streaming = true
        genFrom = 0L
        onChanged()

        job = scope.launch {
            // 速度统计：从第一个 token 到最后一个 token
            var firstTokenAt = 0L
            var lastTokenAt = 0L
            // 系统提示词永远逐字、完整地放在最前面（前缀缓存），只裁历史，且按整条从最旧裁
            val budget = PromptBudget.historyBudget(if (personaOn) conv.persona else "")
            val (history, dropped) = PromptBudget.fit(conv.messages.take(aiIndex), budget)
            trimNote = if (dropped > 0) "上下文超预算，本轮省略了最早的 $dropped 条消息" else ""
            // 人设后面固定追加执行约束（字符串恒定，不会破坏前缀缓存）；人设模式关掉就整段不注入
            val sysPrompt = if (!personaOn || conv.persona.isBlank()) "" else
                conv.persona.trimEnd() + "\n\n" + PERSONA_HARD_RULES
            // 本轮回执放在历史之后：越靠近生成位置，模型越听话（SillyTavern 的
            // post_history_instructions 就是这个位置）
            val tailPrompt = if (!personaOn || (conv.persona.isBlank() && conv.examples.isBlank())) "" else buildString {
                append("【本轮提醒】现在就以角色的身份回话。只写你自己的台词和动作，1-3 句，口语短句；")
                append("不复述设定、不解释、不写旁白；不替对方说话，不替对方做决定。")
                append("\n\n【剧情推进】你在演一场戏，不是陪聊：\n")
                append("- 每轮至少推进一样：时间、地点、信息、情绪或事件，不要原地寒暄。\n")
                append("- 用户给出剧情或行动时，先接住，再顺势往下演；不要否定或跳过用户给的情节。\n")
                append("- 用户没给方向时，自己从当前场景里找下一步：有人经过、话题被戳到痛处、对方说漏一句。\n")
                append("- 结尾留一个具体的钩子（一个动作或一句话），别把话头原样丢回去。")
                if (conv.rpMode) {
                    append("\n\n【输出格式】每段单独一行，行首加标签，没有的标签就不写：\n")
                    append("【说】一句台词（不加引号，不加冒号）\n")
                    append("【做】一个动作或神态\n")
                    append("【想】一句没说出口的心理\n")
                    append("不要写成连续段落，不要旁白解释，不要加时间戳。")
                }
                if (conv.examples.isNotBlank()) {
                    append("\n\n照下面这个语气和节奏说话（只学说话方式，不要照抄内容）：\n")
                    append(conv.examples.trim())
                }
            }
            DeepSeek.stream(
                apiKey = settings.apiKey,
                baseUrl = settings.baseUrl,
                apiPath = settings.apiPath,
                model = settings.model,
                thinking = conv.thinking,
                webSearch = conv.webSearch,
                compat = settings.isCompat(),
                systemPrompt = sysPrompt,
                tail = tailPrompt,
                history = history,
            ).collect { chunk ->
                when (chunk) {
                    is Chunk.Delta -> {
                        val now = System.currentTimeMillis()
                        if (firstTokenAt == 0L) {
                            firstTokenAt = now
                            genFrom = now
                        }
                        lastTokenAt = now
                        val cur = conv.messages[aiIndex]
                        conv.messages[aiIndex] = cur.copy(content = cur.content + chunk.text)
                    }
                    is Chunk.Reasoning -> {
                        val now = System.currentTimeMillis()
                        if (firstTokenAt == 0L) {
                            firstTokenAt = now
                            genFrom = now
                        }
                        lastTokenAt = now
                        val cur = conv.messages[aiIndex]
                        conv.messages[aiIndex] = cur.copy(reasoning = cur.reasoning + chunk.text)
                    }
                    is Chunk.Searching -> {
                        searching = chunk.kind
                    }
                    is Chunk.Sources -> {
                        val cur = conv.messages[aiIndex]
                        conv.messages[aiIndex] = cur.copy(sources = chunk.urls)
                        searching = ""
                    }
                    is Chunk.Usage -> {
                        val cur = conv.messages[aiIndex]
                        conv.messages[aiIndex] = cur.copy(
                            promptTokens = chunk.prompt,
                            completionTokens = chunk.completion,
                            cachedTokens = chunk.cached,
                        )
                    }
                    is Chunk.Done -> {
                        val cur0 = conv.messages[aiIndex]
                        val decode = if (firstTokenAt > 0 && lastTokenAt > firstTokenAt) lastTokenAt - firstTokenAt else 0L
                        conv.messages[aiIndex] = cur0.copy(
                            decodeMs = decode,
                            content = if (chunk.error != null) cur0.content + "\n\n⚠ ${chunk.error}" else cur0.content,
                        )
                        searching = ""
                        streaming = false
                        conv.updatedAt = System.currentTimeMillis()
                        onChanged()
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = TextMain,
                modifier = Modifier.clickable { onBack() }.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                conv.title.ifBlank { "新对话" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (personaOn && conv.persona.isNotBlank()) {
                Box(
                    Modifier
                        .background(Color(0xFFEFF3FF), RoundedCornerShape(10.dp))
                        .clickable { onOpenPersona() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("人设", color = Accent, fontSize = 11.sp) }
                Spacer(Modifier.width(6.dp))
            }
            Box {
                Text(
                    "⋯",
                    fontSize = 22.sp,
                    color = TextMuted,
                    modifier = Modifier.clickable { showMenu = true }.padding(horizontal = 8.dp),
                )
                androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (personaOn) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (conv.persona.isBlank()) "对话设定" else "对话设定 · 已启用") },
                            onClick = {
                                showMenu = false
                                onOpenPersona()
                            },
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("导出 Markdown") },
                        onClick = {
                            showMenu = false
                            pendingExport = conv.exportMarkdown(settings.model)
                            val safe = conv.title.replace(Regex("[\\\\/:*?\"<>|]"), "-").take(30).ifBlank { "对话" }
                            exportLauncher.launch("$safe.md")
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = { showMenu = false; newTitle = conv.title; renaming = true },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("删除对话", color = Color(0xFFD64343)) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(conv.messages.size) { idx ->
                MessageRow(conv.messages[idx], rp = personaOn && conv.rpMode && conv.persona.isNotBlank())
            }
        }

        Surface(color = Color.White, shadowElevation = 0.dp) {
          Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleChip("深度思考", conv.thinking, enabled = !compat) {
                    conv.thinking = it
                    onChanged()
                }
                ToggleChip("联网搜索", conv.webSearch, enabled = !compat) {
                    conv.webSearch = it
                    onChanged()
                }
                if (compat) {
                    Text("第三方接口：这两项不可用", fontSize = 10.sp, color = TextMuted)
                }
                if (streaming && genFrom > 0) {
                    val est = PromptBudget.estimateTokens(conv.messages.lastOrNull()?.content.orEmpty())
                    val secs = (System.currentTimeMillis() - genFrom) / 1000.0
                    if (secs > 0.5 && est > 8) {
                        Text(
                            "≈${(est / secs).toInt()} tok/s",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }
                if (searching.isNotBlank()) {
                    Text(
                        if (searching.contains("deep")) "深度思考搜索中…" else "正在联网…",
                        fontSize = 11.sp,
                        color = Accent,
                    )
                }
            }
            if (trimNote.isNotBlank()) {
                Text(
                    trimNote,
                    fontSize = 11.sp,
                    color = Color(0xFFB26B00),
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                )
            }
            if (attachments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { a ->
                        Row(
                            Modifier
                                .background(Color(0xFFF3F5F8), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                a.name,
                                fontSize = 12.sp,
                                color = TextMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "✕",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.clickable { attachments.remove(a) },
                            )
                        }
                    }
                }
            }
            if (attachNote.isNotBlank()) {
                Text(
                    attachNote,
                    fontSize = 11.sp,
                    color = Color(0xFFD64343),
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier.size(38.dp).clickable { fileLauncher.launch(arrayOf("*/*")) },
                    contentAlignment = Alignment.Center,
                ) { Text("📎", fontSize = 17.sp, color = TextMuted) }
                Spacer(Modifier.width(4.dp))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, Color(0xFFE3E7ED), RoundedCornerShape(20.dp)),
                    placeholder = { Text("说点什么…", color = TextMuted, fontSize = 15.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (streaming) { job?.cancel(); streaming = false } else send() },
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier.size(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (streaming) Color(0xFFD64343) else Accent),
                ) { Text(if (streaming) "■" else "↑", color = Color.White, fontSize = 16.sp) }
                }
            }
          }
        }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    conv.title = newTitle.trim().ifBlank { "新对话" }
                    renaming = false
                    onChanged()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MessageRow(m: Msg, rp: Boolean = false) {
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(Color(0xFFEFF3FF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer { Text(m.content, fontSize = 15.sp, lineHeight = 23.sp, color = TextMain) }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            var showReason by remember { mutableStateOf(false) }
            if (m.reasoning.isNotBlank()) {
                Row(
                    Modifier
                        .clickable { showReason = !showReason }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (if (showReason) "▾ " else "▸ ") + "思考过程",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                    if (m.content.isBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("思考中…", fontSize = 11.sp, color = Accent)
                    }
                }
                if (showReason) {
                    Text(
                        m.reasoning,
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
                    )
                }
            }
            if (m.content.isBlank()) {
                if (m.reasoning.isBlank()) Text("…", fontSize = 15.sp, color = TextMuted)
            } else {
                SelectionContainer {
                    if (rp) RoleplayView(m.content) else MarkdownView(m.content, Accent)
                }
            }

            if (m.sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("来源", fontSize = 11.sp, color = TextMuted)
                Spacer(Modifier.height(3.dp))
                m.sources.take(8).forEach { u ->
                    Text(
                        "· ${hostOf(u)}",
                        fontSize = 11.sp,
                        color = Color(0xFF6B76FF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (m.promptTokens > 0 || m.completionTokens > 0) {
                Spacer(Modifier.height(8.dp))
                Text(usageLabel(m), fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}

/**
 * 角色扮演专用渲染：把模型的【说】【做】【想】拆成三种样式。
 * 台词 → 描边气泡；动作 → 灰色小字；心理 → 灰色括号；没标签的行按叙述走 Markdown。
 */
@Composable
private fun RoleplayView(text: String) {
    val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    Column {
        lines.forEach { line ->
            when {
                line.startsWith("【说】") -> Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .widthIn(max = 300.dp)
                        .border(1.dp, Color(0xFFDDE3EC), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        line.removePrefix("【说】").trim(),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = TextMain,
                    )
                }
                line.startsWith("【做】") -> Text(
                    line.removePrefix("【做】").trim(),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                line.startsWith("【想】") -> Text(
                    "（" + line.removePrefix("【想】").trim() + "）",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF9AA4B2),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                else -> MarkdownView(line, Accent)
            }
        }
    }
}

/* ------------------------------------------------------------------ 设置页 */

@Composable
private fun SettingsScreen(initial: Settings, onBack: () -> Unit, onSave: (Settings) -> Unit) {
    var key by remember { mutableStateOf(initial.apiKey) }
    var base by remember { mutableStateOf(initial.baseUrl) }
    var path by remember { mutableStateOf(initial.apiPath) }
    var model by remember { mutableStateOf(initial.model) }
    var thinking by remember { mutableStateOf(initial.thinking) }
    var webSearch by remember { mutableStateOf(initial.webSearch) }
    var compat by remember { mutableStateOf(initial.compat) }
    var personaMode by remember { mutableStateOf(initial.personaMode) }

    val previewUrl = DeepSeek.buildUrl(base, path)
    val autoCompat = compat || !base.contains("api.deepseek.com", ignoreCase = true)
    val presets = listOf("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v3.2")

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 26.sp, color = TextMain, modifier = Modifier.clickable { onBack() }.padding(horizontal = 6.dp))
            Spacer(Modifier.width(4.dp))
            Text("设置", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextMain)
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("API Key", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("sk-…", color = TextMuted) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text("接口地址（主机）", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = base,
                onValueChange = { base = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.deepseek.com", color = TextMuted, fontSize = 13.sp) },
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(12.dp))
            Text("接口路径", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("/chat/completions", color = TextMuted, fontSize = 13.sp) },
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text("实际请求：$previewUrl", fontSize = 11.sp, color = TextMuted)

            Spacer(Modifier.height(16.dp))
            Text("模型", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.take(3).forEach { p ->
                    ToggleChip(p.removePrefix("deepseek-"), model == p) { model = p }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.drop(3).forEach { p ->
                    ToggleChip(p, model == p) { model = p }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("第三方中转（OpenAI 兼容）", fontSize = 15.sp, color = TextMain)
                    Text(
                        "主机不是 api.deepseek.com 时自动开启；只发标准字段",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
                Switch(checked = compat, onCheckedChange = { compat = it })
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("新对话默认开深度思考", fontSize = 15.sp, color = TextMain)
                    Text("更慢、更贵，复杂问题再用；进对话后还能随时切", fontSize = 12.sp, color = TextMuted)
                }
                Switch(checked = thinking, onCheckedChange = { thinking = it })
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("新对话默认开联网搜索", fontSize = 15.sp, color = TextMain)
                    Text("由模型判断是否搜；开思考时走深度思考搜索", fontSize = 12.sp, color = TextMuted)
                }
                Switch(checked = webSearch, onCheckedChange = { webSearch = it })
            }
            if (autoCompat) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前是第三方接口：深度思考与联网搜索是 DeepSeek 官方能力，这里会自动跳过。",
                    fontSize = 11.sp,
                    color = Color(0xFFB26B00),
                    lineHeight = 16.sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("人设模式（角色扮演）", fontSize = 15.sp, color = TextMain)
                    Text(
                        "打开后，对话里才有「对话设定」：生成角色设定、填示例台词、按台词/动作/心理分句显示。" +
                            "关掉就只是普通助手，这些入口全都不出现。",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp,
                    )
                }
                Switch(checked = personaMode, onCheckedChange = { personaMode = it })
            }
            if (personaMode) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "已打开：新建或打开对话后，点右上角 ⋯ →「对话设定」开始。",
                    fontSize = 11.sp,
                    color = Accent,
                    lineHeight = 16.sp,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    onSave(
                        Settings(
                            apiKey = key.trim(),
                            baseUrl = base.trim(),
                            apiPath = path.trim().ifBlank { "/chat/completions" },
                            model = model.trim(),
                            thinking = thinking,
                            webSearch = webSearch,
                            compat = compat,
                            personaMode = personaMode,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("保存", fontSize = 15.sp) }
            Spacer(Modifier.height(24.dp))
        }
    }
}



private fun timeLabel(ts: Long): String {
    if (ts <= 0) return ""
    val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    return fmt.format(java.util.Date(ts))
}

/** 输入框上方的开关胶囊（对齐官方 App 的「深度思考 / 联网搜索」） */
@Composable
private fun ToggleChip(
    label: String,
    on: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val active = on && enabled
    Row(
        Modifier
            .background(
                if (active) Color(0xFFEFF3FF) else Color(0xFFF3F5F8),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (active) Accent.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled) { onChange(!on) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (on) "●" else "○",
            fontSize = 9.sp,
            color = if (!enabled) Color(0xFFCED4DC) else if (on) Accent else TextMuted,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (!enabled) Color(0xFFCED4DC) else if (on) Accent else TextMuted,
        )
    }
}

private fun fmtNum(n: Int): String = String.format(java.util.Locale.CHINA, "%,d", n)

/** 只有官方域名才发 thinking / tools；第三方中转自动走标准 OpenAI 字段 */
private fun Settings.isCompat(): Boolean =
    compat || !baseUrl.contains("api.deepseek.com", ignoreCase = true)

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

private fun usageLabel(m: Msg): String {
    val total = m.promptTokens + m.completionTokens
    val sb = StringBuilder("输入 ").append(fmtNum(m.promptTokens))
    if (m.cachedTokens > 0 && m.promptTokens > 0) {
        sb.append("（缓存 ").append(fmtNum(m.cachedTokens))
            .append(" · ").append(m.cachedTokens * 100 / m.promptTokens).append("%）")
    }
    sb.append(" · 输出 ").append(fmtNum(m.completionTokens))
    if (m.decodeMs >= 300 && m.completionTokens > 0) {
        val speed = m.completionTokens * 1000.0 / m.decodeMs
        sb.append(" · ").append(String.format(java.util.Locale.CHINA, "%.1f", speed)).append(" tok/s")
    }
    sb.append(" · 合计 ").append(fmtNum(total)).append(" tokens")
    return sb.toString()
}

/* ------------------------------------------------------------------ 对话设定 / 附件 */

private const val PERSONA_META = """你是「提示词工程师」。用户会描述他在某个对话里想要的助手角色或任务，可能还附上材料（文档、笔记、代码、规范、现成的角色设定等）。请据此写出一份可直接作为 system prompt 使用的完整设定。

要求：
1. 只输出提示词正文，不要解释，不要加标题，不要包代码块。
2. 用第二人称写（"你是……"），中文。
3. 覆盖：身份与专长、任务目标、工作流程、回答风格与语气、规则与边界（明确不做什么）、输出格式偏好、信息不足时怎么办。
4. 材料里的具体要求和约束必须逐条落进提示词，一条都不能丢；材料很长时也不要概括掉细节要求。
5. 如果材料本身就是一份现成的角色设定 / 人设（含外貌、背景、口癖、禁用词、示例台词），必须**原样保留所有具体细节、口癖和示例**，不得改写、删减或"润色"成自己的话；补上缺少的执行约束即可。
6. 篇幅由内容决定：意图简单时 300-600 字；材料多、要求多就写够。
7. 具体、可执行，避免空话套话；不要编造用户没提供的事实，不要承诺做不到的能力。
"""

/**
 * 固定追加在人设后面的一段执行约束（每次请求同样的字符串，不影响前缀缓存）。
 * 没有它，模型容易把人设当"参考资料"，回成通用的暖男/助手腔。
 */
private const val PERSONA_HARD_RULES = """【执行约束｜优先级高于以上所有内容】
- 你就是上面这个人。不要复述设定，不要解释自己在扮演，不要跳出角色。
- 每次回复 1-3 句，口语短句；不写比喻，不写解释性旁白，不做阅读理解式的分析。
- 用词、句长、口癖、性格缺陷必须体现这个人；不要写成通用的、听话的 AI 助手。
- 只写你自己的动作和台词；不替对方说话，不替对方做决定，不揣测对方的表情和心理。
- 宁可少说一句，也不要脱离人设去讨好或解释。"""

data class Attachment(val name: String, val text: String)

private fun readAttachment(ctx: android.content.Context, uri: Uri): Attachment? {
    val resolver = ctx.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "附件"

    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null

    if (bytes.isEmpty()) return Attachment(name, "（空文件）")
    val head = bytes.copyOf(minOf(bytes.size, 4096))
    if (head.any { it == 0.toByte() }) return Attachment(name, "（二进制文件，这里只能读文本，内容未解析）")

    val limit = 200_000
    val text = if (bytes.size > limit) {
        String(bytes, 0, limit, Charsets.UTF_8) + "\n…（文件较大，只取了前 200KB）"
    } else {
        String(bytes, Charsets.UTF_8)
    }
    return Attachment(name, text)
}

private fun buildUserContent(typed: String, files: List<Attachment>): String {
    if (files.isEmpty()) return typed
    val sb = StringBuilder()
    if (typed.isNotBlank()) sb.append(typed.trim()).append("\n\n")
    files.forEach { f ->
        sb.append("【附件：").append(f.name).append("】\n")
        sb.append("```text\n").append(f.text.trim()).append("\n```\n\n")
    }
    return sb.toString().trim()
}

@Composable
private fun PersonaScreen(conv: Conversation, settings: Settings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var idea by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var produced by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var rawMode by remember { mutableStateOf(false) }
    val files = remember { mutableStateListOf<Attachment>() }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        note = ""
        uris.forEach { uri ->
            val a = runCatching { readAttachment(ctx, uri) }.getOrNull()
            if (a == null) note = "有一个文件读不出来，已跳过"
            else if (files.none { it.name == a.name && it.text == a.text }) files.add(a)
        }
    }

    fun generate() {
        if ((idea.isBlank() && files.isEmpty()) || busy) return
        // 原样模式：材料本身就是写好的设定，一个字都不改
        if (rawMode && files.isNotEmpty()) {
            conv.persona = files.joinToString("\n\n") { it.text.trim() }
            files.clear()
            idea = ""
            onBack()
            return
        }
        val input = buildString {
            append("请为这个对话生成一份系统提示词。")
            append("\n\n用户意图：").append(idea.trim().ifBlank { "（用户没写，按下面的材料推断）" })
            if (files.isNotEmpty()) {
                append("\n\n用户提供的材料（可能很长，请完整吸收，不要漏掉里面的要求）：")
                files.forEach { f ->
                    append("\n\n【材料：").append(f.name).append("】\n```text\n")
                        .append(f.text.trim()).append("\n```")
                }
            }
        }
        busy = true
        err = ""
        produced = 0
        scope.launch {
            val sb = StringBuilder()
            DeepSeek.stream(
                apiKey = settings.apiKey,
                baseUrl = settings.baseUrl,
                apiPath = settings.apiPath,
                model = settings.model,
                thinking = false,
                webSearch = false,
                compat = settings.isCompat(),
                systemPrompt = PERSONA_META,
                history = listOf(Msg("user", input)),
            ).collect { chunk ->
                when (chunk) {
                    is Chunk.Delta -> {
                        sb.append(chunk.text)
                        produced = sb.length
                    }
                    is Chunk.Done -> {
                        busy = false
                        if (chunk.error != null) {
                            err = chunk.error
                        } else if (sb.isNotBlank()) {
                            conv.persona = sb.toString().trim()
                            idea = ""
                            files.clear()
                            onBack()
                        } else {
                            err = "模型没返回内容，再试一次"
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = TextMain,
                modifier = Modifier.clickable { onBack() }.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "对话设定",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextMain,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onBack() }) { Text("完成", fontSize = 15.sp, color = Accent) }
        }
        HorizontalDivider(color = Color(0xFFF0F2F5))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // 状态卡：只说「有没有、多大」，正文不给看
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (conv.persona.isNotBlank()) Color(0xFFEFF3FF) else Color(0xFFF7F8FA),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(14.dp),
            ) {
                Column {
                    val pt = PromptBudget.estimateTokens(conv.persona)
                    Text(
                        if (conv.persona.isNotBlank()) "已启用 · 约 ${PromptBudget.humanTokens(pt)} tokens" else "还没有设定",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (conv.persona.isNotBlank()) Accent else TextMain,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "提示词由模型生成，不展示正文。每轮请求会把它完整、逐字地放在最前面注入；" +
                            "只在生成时处理一次，之后不再改写（前缀稳定才能一直命中缓存）。",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp,
                    )
                    if (conv.persona.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { conv.persona = "" },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("停用这个对话的设定", fontSize = 12.sp, color = TextMuted) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("示例台词（可选，对语气影响最大）", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(2.dp))
            Text(
                "贴 1-2 段你希望它怎么说话的原文；每轮都会作为「照着这个节奏」的参考放在末尾。",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = conv.examples,
                onValueChange = { conv.examples = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = {
                    Text(
                        "例：\n我：你今天怎么这么安静。\n裴郁：你在意吗。还是只是随口问一句。",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                },
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("分句显示（台词 / 动作 / 心理）", fontSize = 14.sp, color = TextMain)
                    Text(
                        "角色扮演模式：模型按【说】【做】【想】分行输出，界面把台词做成气泡、动作和心理灰字",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp,
                    )
                }
                Switch(checked = conv.rpMode, onCheckedChange = { conv.rpMode = it })
            }

            Spacer(Modifier.height(18.dp))
            Text("你想要什么样的助手？（一句话就行）", fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = idea,
                onValueChange = { idea = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("例：当严格的技术面试官，专问 Android，每题追问三次", color = TextMuted, fontSize = 13.sp)
                },
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "也可以上传材料，让模型照着写",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { fileLauncher.launch(arrayOf("*/*")) }) {
                    Text("📎 上传文件", fontSize = 13.sp, color = Accent)
                }
            }
            if (files.isNotEmpty()) {
                files.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            f.name,
                            fontSize = 12.sp,
                            color = TextMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${f.text.length} 字",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "✕",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.clickable { files.remove(f) },
                        )
                    }
                }
            }
            if (note.isNotBlank()) {
                Text(note, fontSize = 11.sp, color = Color(0xFFD64343))
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("原样使用材料（不重写）", fontSize = 14.sp, color = TextMain)
                    Text(
                        "材料本身就是写好的设定时选这个，模型不动一个字",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
                Switch(checked = rawMode, onCheckedChange = { rawMode = it })
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { generate() },
                enabled = (idea.isNotBlank() || files.isNotEmpty()) && !busy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    when {
                        busy -> "生成中… 已 ${PromptBudget.humanTokens(produced)} 字"
                        rawMode && files.isNotEmpty() -> "直接用这份材料"
                        conv.persona.isNotBlank() -> "重新生成"
                        else -> "生成并启用"
                    },
                    fontSize = 15.sp,
                )
            }
            if (err.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(err, fontSize = 12.sp, color = Color(0xFFD64343))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "说明：生成好后直接生效，本对话每一轮都带这段提示词；" +
                    "材料只在生成时读一次，不会每轮重发。",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}