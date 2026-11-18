package app.lightchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface Chunk {
    data class Delta(val text: String) : Chunk
    data class Reasoning(val text: String) : Chunk
    /** 服务端联网工具开始工作，action = search / open_page / find_in_page / read_link… */
    data class Searching(val kind: String) : Chunk
    /** 本轮引用的来源（去重后） */
    data class Sources(val urls: List<String>) : Chunk
    /** 计费用量（最后一个 chunk 才有） */
    data class Usage(val prompt: Int, val completion: Int, val cached: Int) : Chunk
    data class Done(val error: String?) : Chunk
}

object DeepSeek {

    /**
     * 流式请求。
     *
     * - [thinking] → `thinking: {type: enabled/disabled}`
     * - [webSearch] → 声明服务端工具 `deepseek_web_search`（官方全家桶 API）
     * - [compat] → 第三方中转：只发标准 OpenAI 字段
     * - [systemPrompt] → 人设，放在最前面（前缀稳定，吃缓存）
     * - [tail] → 本轮回执，放在**历史之后**（越靠近生成位置，模型越听话）；
     *   若服务端不接受末尾的 system 消息，会自动去掉它重试一次
     */
    fun stream(
        apiKey: String,
        baseUrl: String,
        apiPath: String = "/chat/completions",
        model: String,
        thinking: Boolean,
        webSearch: Boolean,
        compat: Boolean = false,
        systemPrompt: String = "",
        tail: String = "",
        history: List<Msg>,
    ): Flow<Chunk> = flow {
        if (apiKey.isBlank()) {
            emit(Chunk.Done("还没填 API Key，去设置里填一下"))
            return@flow
        }

        val endpoint = buildUrl(baseUrl, apiPath)
        var useTail = tail.isNotBlank()
        var attempt = 0
        var lastError: String? = null

        while (attempt < 2) {
            attempt++

            val messages = JSONArray()
            if (systemPrompt.isNotBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            history.forEach { m ->
                if (m.content.isNotBlank()) {
                    messages.put(JSONObject().put("role", m.role).put("content", m.content))
                }
            }
            if (useTail) {
                messages.put(JSONObject().put("role", "system").put("content", tail))
            }

            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("stream", true)
                .put("max_tokens", PromptBudget.MAX_OUTPUT)

            if (!compat) {
                body.put("stream_options", JSONObject().put("include_usage", true))
                body.put("thinking", JSONObject().put("type", if (thinking) "enabled" else "disabled"))
                if (webSearch) {
                    body.put(
                        "tools",
                        JSONArray().put(JSONObject().put("type", "deepseek_web_search")),
                    )
                }
            }

            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = 300_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Accept", "text/event-stream")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = runCatching {
                        conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    }.getOrDefault("")
                    // 末尾的 system 消息被拒（部分网关只允许 system 出现在开头）：去掉再试一次
                    if (code == 400 && useTail) {
                        useTail = false
                        continue
                    }
                    lastError = when (code) {
                        401 -> "API Key 无效或已失效"
                        402 -> "DeepSeek 账户余额不足"
                        429 -> "请求太频繁，稍等再发"
                        else -> "HTTP $code ${err.take(300)}"
                    }
                    break
                }

                val sources = LinkedHashSet<String>()
                var usageSent = false
                var serverError: String? = null

                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        if (payload.isEmpty()) continue
                        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: continue

                        obj.optJSONObject("error")?.let { e ->
                            serverError = e.optString("message", "生成失败")
                        }

                        obj.optJSONObject("usage")?.let { u ->
                            if (!usageSent) {
                                usageSent = true
                                emit(
                                    Chunk.Usage(
                                        prompt = u.optInt("prompt_tokens", 0),
                                        completion = u.optInt("completion_tokens", 0),
                                        cached = u.optInt("prompt_cache_hit_tokens", 0),
                                    )
                                )
                            }
                        }

                        val choices = obj.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val choice = choices.getJSONObject(0)

                        choice.optJSONObject("server_tool_event")?.let { ev ->
                            if (ev.optString("type") == "call_started") {
                                val kind = ev.optJSONObject("call")?.optString("kind").orEmpty()
                                emit(Chunk.Searching(kind.ifBlank { "search" }))
                            }
                        }

                        val delta = choice.optJSONObject("delta") ?: continue

                        // Android 的 org.json 会把 JSON 的 null 转成字面量 "null"，必须自己判空
                        str(delta, "reasoning_content")
                            .takeIf { it.isNotEmpty() }?.let { emit(Chunk.Reasoning(it)) }
                        str(delta, "content")
                            .takeIf { it.isNotEmpty() }?.let { emit(Chunk.Delta(it)) }

                        delta.optJSONArray("annotations")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                arr.getJSONObject(i).optJSONObject("url_citation")
                                    ?.optString("url").orEmpty().takeIf { it.isNotBlank() }
                                    ?.let { sources.add(it) }
                            }
                        }
                        delta.optJSONArray("server_tool_calls")?.let { steps ->
                            for (i in 0 until steps.length()) {
                                val calls = steps.getJSONObject(i).optJSONArray("calls") ?: continue
                                for (j in 0 until calls.length()) {
                                    val c = calls.getJSONObject(j)
                                    c.optJSONArray("sources")?.let { arr ->
                                        for (k in 0 until arr.length()) {
                                            arr.getJSONObject(k).optString("url").takeIf { it.isNotBlank() }
                                                ?.let { sources.add(it) }
                                        }
                                    }
                                    c.optJSONObject("page")?.optString("url")?.takeIf { it.isNotBlank() }
                                        ?.let { sources.add(it) }
                                }
                            }
                        }
                    }
                }

                if (sources.isNotEmpty()) emit(Chunk.Sources(sources.toList()))
                emit(Chunk.Done(serverError))
                return@flow
            } catch (e: Exception) {
                emit(Chunk.Done(e.message ?: "网络错误"))
                return@flow
            } finally {
                runCatching { conn?.disconnect() }
            }
        }

        emit(Chunk.Done(lastError ?: "请求失败"))
    }.flowOn(Dispatchers.IO)

    /**
     * 拼接口地址：baseUrl 已经带了 /chat/completions 就直接用；
     * 否则 baseUrl 去尾斜杠 + apiPath（兼容 /v1/chat/completions 这类路径）。
     */
    fun buildUrl(baseUrl: String, apiPath: String): String {
        val b = baseUrl.trim()
        if (b.endsWith("/chat/completions")) return b
        val p = apiPath.trim().ifBlank { "/chat/completions" }
        return b.trimEnd('/') + "/" + p.trimStart('/')
    }

    /** 安全取字符串：null / JSONObject.NULL / 字面量 "null" 都当空串。 */
    fun str(o: JSONObject?, key: String): String {
        if (o == null) return ""
        val v = o.opt(key) ?: return ""
        if (v === JSONObject.NULL) return ""
        val s = v.toString()
        return if (s == "null") "" else s
    }
}
