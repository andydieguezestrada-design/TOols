package com.tools.maestro.workspace.diff

import com.tools.maestro.agent.ProposedChange

/** Compact line-oriented diff using an LCS backbone instead of dumping both whole files. */
object UnifiedDiff {
    private data class Op(val type: Char, val line: String)

    fun render(change: ProposedChange): String {
        if (change.before == change.after) return ""
        val old = normalize(change.before)
        val new = normalize(change.after)
        val a = old.split("\n")
        val b = new.split("\n")
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val ops = mutableListOf<Op>()
        var i = 0; var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> { ops += Op(' ', a[i]); i++; j++ }
                j < b.size && (i == a.size || dp[i][j + 1] >= dp[i + 1][j]) -> { ops += Op('+', b[j]); j++ }
                else -> { ops += Op('-', a[i]); i++ }
            }
        }
        val out = StringBuilder("--- a/${change.path}\n+++ b/${change.path}\n")
        val limit = 800
        ops.take(limit).forEach { out.append(it.type).append(it.line).append('\n') }
        if (ops.size > limit) out.append("… diff truncated after $limit lines …\n")
        return out.toString()
    }

    private fun normalize(s: String) = s.replace("\r\n", "\n").replace("\r", "\n").removeSuffix("\n")
}
