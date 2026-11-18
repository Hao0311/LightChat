package app.lightchat

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Msg(
    val role: String,
    val content: String = "",
    val reasoning: String = "",
    /** 本轮输入 tokens */
    val promptTokens: Int = 0,
    /** 本轮输出 tokens */
    val completionTokens: Int = 0,
    /** 命中前缀缓存的输入 tokens */
    val cachedTokens: Int = 0,
    /** 本轮联网了几次 */
    val searches: Int = 0,
    /** 从第一个 token 到最后一个 token 的耗时（毫秒），用来算生成速度 */
    val decodeMs: Long = 0,
    /** 引用来源 */
    val sources: List<String> = emptyList(),
)

class Conversation(
    val id: String = UUID.randomUUID().toString(),
    title: String = "新对话",
    updatedAt: Long = System.currentTimeMillis(),
    persona: String = "",
    thinking: Boolean = false,
    webSearch: Boolean = false,
    examples: String = "",
    rpMode: Boolean = true,
) {
    // 用 Compose 的可观察状态：改了立刻重绘（之前是普通 var，点开关不刷新）
    var title by mutableStateOf(title)
    var updatedAt by mutableStateOf(updatedAt)
    /** 这个对话专属的系统提示词；只有模型生成，界面上不给看。空字符串表示不注入。 */
    var persona by mutableStateOf(persona)
    /** 深度思考开关（每轮请求带上） */
    var thinking by mutableStateOf(thinking)
    /** 联网搜索开关 */
    var webSearch by mutableStateOf(webSearch)
    /** 示例台词：教模型这个角色怎么说话（可选） */
    var examples by mutableStateOf(examples)
    /** 角色扮演模式：台词/动作/心理分开渲染（只对人设对话生效） */
    var rpMode by mutableStateOf(rpMode)

    val messages = mutableStateListOf<Msg>()
}

data class Settings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    /** 接口路径；官方是 /chat/completions，中转常见 /v1/chat/completions */
    val apiPath: String = "/chat/completions",
    val model: String = "deepseek-flash",
    val thinking: Boolean = false,
    val webSearch: Boolean = false,
    /** OpenAI 兼容模式：不发送 thinking / tools 这类 DeepSeek 专有字段 */
    val compat: Boolean = false,
    /** 人设模式：打开后对话才有「对话设定 / 示例台词 / 分句显示」这些角色扮演功能 */
    val personaMode: Boolean = false,
)

object Store {
    private const val PREF = "lightchat"
    private const val K_CONV = "conversations"
    private const val K_KEY = "apiKey"
    private const val K_BASE = "baseUrl"
    private const val K_MODEL = "model"
    private const val K_THINK = "thinking"
    private const val K_WEB = "webSearch"
    private const val K_PATH = "apiPath"
    private const val K_COMPAT = "compat"
    private const val K_PERSONA_MODE = "personaMode"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun loadSettings(ctx: Context): Settings {
        val p = prefs(ctx)
        return Settings(
            apiKey = p.getString(K_KEY, "") ?: "",
            baseUrl = p.getString(K_BASE, "https://api.deepseek.com") ?: "https://api.deepseek.com",
            apiPath = p.getString(K_PATH, "/chat/completions") ?: "/chat/completions",
            model = p.getString(K_MODEL, "deepseek-flash") ?: "deepseek-flash",
            thinking = p.getBoolean(K_THINK, false),
            webSearch = p.getBoolean(K_WEB, false),
            compat = p.getBoolean(K_COMPAT, false),
            personaMode = p.getBoolean(K_PERSONA_MODE, false),
        )
    }

    fun saveSettings(ctx: Context, s: Settings) {
        prefs(ctx).edit()
            .putString(K_KEY, s.apiKey)
            .putString(K_BASE, s.baseUrl)
            .putString(K_PATH, s.apiPath)
            .putString(K_MODEL, s.model)
            .putBoolean(K_THINK, s.thinking)
            .putBoolean(K_WEB, s.webSearch)
            .putBoolean(K_COMPAT, s.compat)
            .putBoolean(K_PERSONA_MODE, s.personaMode)
            .apply()
    }

    fun loadConversations(ctx: Context): List<Conversation> {
        val raw = prefs(ctx).getString(K_CONV, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Conversation(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    title = o.optString("title", "新对话"),
                    updatedAt = o.optLong("updatedAt", 0L),
                    persona = o.optString("persona", ""),
                    thinking = o.optBoolean("thinking", false),
                    webSearch = o.optBoolean("webSearch", false),
                    examples = o.optString("examples", ""),
                    rpMode = o.optBoolean("rpMode", true),
                ).also { c ->
                    val ms = o.optJSONArray("messages") ?: JSONArray()
                    for (j in 0 until ms.length()) {
                        val m = ms.getJSONObject(j)
                        val srcs = m.optJSONArray("sources")
                        c.messages.add(
                            Msg(
                                role = m.optString("role", "user"),
                                content = clean(m.optString("content", "")),
                                reasoning = clean(m.optString("reasoning", "")),
                                promptTokens = m.optInt("promptTokens", 0),
                                completionTokens = m.optInt("completionTokens", 0),
                                cachedTokens = m.optInt("cachedTokens", 0),
                                searches = m.optInt("searches", 0),
                                decodeMs = m.optLong("decodeMs", 0L),
                                sources = if (srcs == null) emptyList() else
                                    (0 until srcs.length()).map { srcs.getString(it) },
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveConversations(ctx: Context, list: List<Conversation>) {
        val arr = JSONArray()
        list.forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("title", c.title)
            o.put("updatedAt", c.updatedAt)
            o.put("persona", c.persona)
            o.put("thinking", c.thinking)
            o.put("webSearch", c.webSearch)
            o.put("examples", c.examples)
            o.put("rpMode", c.rpMode)
            val ms = JSONArray()
            c.messages.forEach { m ->
                val srcs = JSONArray()
                m.sources.forEach { srcs.put(it) }
                ms.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("content", m.content)
                        .put("reasoning", m.reasoning)
                        .put("promptTokens", m.promptTokens)
                        .put("completionTokens", m.completionTokens)
                        .put("cachedTokens", m.cachedTokens)
                        .put("searches", m.searches)
                        .put("decodeMs", m.decodeMs)
                        .put("sources", srcs)
                )
            }
            o.put("messages", ms)
            arr.put(o)
        }
        prefs(ctx).edit().putString(K_CONV, arr.toString()).apply()
    }
}

/** 老版本可能把 JSON 的 null 存成了字面量 "null"，读出来当空。 */
internal fun clean(s: String?): String =
    if (s == null || s == "null" || s == "NULL") "" else s

fun Conversation.exportMarkdown(model: String): String {
    val sb = StringBuilder()
    sb.append("# ").append(title).append("\n\n")
    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(updatedAt))
    sb.append("> 导出时间 ").append(time).append(" · 模型 ").append(model)
        .append(" · ").append(messages.count { it.role == "user" }).append(" 轮\n\n")
    if (persona.isNotBlank()) {
        sb.append("## 对话设定\n\n").append(persona.trim()).append("\n\n")
    }
    messages.forEach { m ->
        if (m.content.isBlank()) return@forEach
        sb.append(if (m.role == "user") "## 我\n\n" else "## AI\n\n")
        sb.append(m.content.trim()).append("\n\n")
    }
    return sb.toString()
}
