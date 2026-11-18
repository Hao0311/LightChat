package app.lightchat

import kotlin.math.ceil

/**
 * 上下文预算。
 *
 * 关键事实（DeepSeek 官方文档，2026-11 核对）：
 * - deepseek-flash = V4.1-Flash，窗口 1,048,576 tokens，输出上限 393,216。
 * - 计费走「自动前缀缓存」：服务端按 token 前缀命中，前缀任何改动都会让该点之后全部失效。
 *   命中价约为未命中的 1/10。所以系统提示词必须**逐字稳定地放在最前面**，
 *   绝不能每轮压缩/改写它，否则缓存永远打不中。
 *
 * 这里只做两件事：
 * 1. 估 token（不引入 tokenizer，中文≈0.6/字，其余≈0.3/字符，偏保守）；
 * 2. 超预算时从最旧的整条消息开始丢，绝不截断系统提示词、不截断单条消息。
 */
object PromptBudget {

    /** 模型窗口（保守取 1M，官方 1,048,576）。 */
    const val CONTEXT_WINDOW = 1_000_000

    /** 给输出预留的额度，和请求里的 max_tokens 对齐。 */
    const val MAX_OUTPUT = 8_192

    /** 估算误差与协议开销的余量。 */
    const val SAFETY = 8_192

    /** 高水位：超过这个值在界面上给红字提示。 */
    const val WARN_TOKENS = 300_000

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            val code = ch.code
            when {
                code in 0x2E80..0x9FFF -> cjk++          // 中日韩统一表意文字
                code in 0xF900..0xFAFF -> cjk++          // 兼容表意
                code in 0xFF00..0xFFEF -> cjk++          // 全角标点
                code in 0x3040..0x30FF -> cjk++          // 假名
                else -> other++
            }
        }
        return ceil(cjk * 0.6 + other * 0.3).toInt()
    }

    /** 系统提示词占用之后，留给历史的预算（tokens）。 */
    fun historyBudget(systemPrompt: String): Int =
        CONTEXT_WINDOW - MAX_OUTPUT - SAFETY - estimateTokens(systemPrompt)

    /** 单条消息的估算开销（含 role/分隔符）。 */
    private fun cost(m: Msg): Int = estimateTokens(m.content) + 8

    /**
     * 从最旧的整条消息开始丢，直到装进预算。
     * @return 保留的历史 + 被丢掉的条数（0 表示没动）。
     */
    fun fit(history: List<Msg>, budget: Int): Pair<List<Msg>, Int> {
        if (history.isEmpty()) return history to 0
        if (budget <= 0) return emptyList<Msg>() to history.size

        val kept = ArrayList<Msg>(history.size)
        var used = 0
        for (i in history.indices.reversed()) {
            val c = cost(history[i])
            if (used + c > budget) break
            used += c
            kept.add(history[i])
        }
        kept.reverse()
        return kept to (history.size - kept.size)
    }

    /** 界面上显示的比例，0..1。 */
    fun usage(systemPrompt: String, history: List<Msg>): Float {
        val used = estimateTokens(systemPrompt) + history.sumOf { cost(it) } + MAX_OUTPUT
        return (used.toFloat() / CONTEXT_WINDOW).coerceIn(0f, 1f)
    }

    fun humanTokens(n: Int): String =
        if (n < 1000) "$n" else String.format("%.1fk", n / 1000.0)
}
