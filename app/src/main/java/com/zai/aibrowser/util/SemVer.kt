package com.zai.aibrowser.util

/**
 * Tiny semantic-version comparison used by the self-updater to decide whether a
 * GitHub release tag (e.g. "v1.2.3") is newer than the running version.
 */
object SemVer {

    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = if (i < pa.size) pa[i] else 0
            val y = if (i < pb.size) pb[i] else 0
            if (x != y) return x - y
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun parse(version: String): List<Int> {
        var v = version.trim()
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1)
        v = v.substringBefore('-')
        val parts = v.split('.')
        val result = ArrayList<Int>(parts.size)
        for (p in parts) {
            val digits = p.filter { it.isDigit() }
            result.add(if (digits.isEmpty()) 0 else digits.toInt())
        }
        return result
    }
}
