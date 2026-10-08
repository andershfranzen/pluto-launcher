package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import java.text.Normalizer
import java.util.Locale

object SearchMatcher {
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("\\s+")

    private const val RANK_PREFIX = 0
    private const val RANK_WORD_PREFIX = 1
    private const val RANK_SUBSTRING = 2
    private const val RANK_PACKAGE = 3

    /**
     * Letters with no canonical decomposition, so NFD leaves them intact. Folded to the
     * ASCII spelling people type without the accent (applied after lower-casing).
     */
    private val letterFolds: Map<Char, String> = mapOf(
        'ø' to "o",
        'ł' to "l",
        'đ' to "d",
        'ð' to "d",
        'ß' to "ss",
        'æ' to "ae",
        'œ' to "oe",
        'ı' to "i",
        'þ' to "th",
        'ħ' to "h",
        'ŧ' to "t",
        'ŀ' to "l",
    )

    /**
     * Lower-cases (Locale.ROOT), strips accents/diacritics (NFD + remove combining marks),
     * folds letters without a decomposition (ø, ł, ß, æ, …), trims, collapses whitespace.
     */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val lower = combiningMarks.replace(decomposed, "").lowercase(Locale.ROOT)
        return fold(lower)
            .trim()
            .replace(whitespace, " ")
    }

    private fun fold(text: String): String {
        if (text.none { it in letterFolds }) return text
        return buildString(text.length + 4) {
            for (c in text) {
                val replacement = letterFolds[c]
                if (replacement != null) append(replacement) else append(c)
            }
        }
    }

    /** True if the normalized query is contained in the normalized label or package name. Blank query matches all. */
    fun matches(entry: AppEntry, query: String): Boolean {
        val q = normalize(query)
        return q.isEmpty() || rank(entry, q) != null
    }

    /**
     * Filters and ranks: label prefix, then label word-prefix, then label substring,
     * then package-name substring; ties keep the input (alphabetical) order.
     * Blank query returns [apps] unchanged.
     */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return apps
        return apps.mapNotNull { entry -> rank(entry, q)?.let { entry to it } }
            .sortedBy { it.second } // stable sort: equal ranks keep input order
            .map { it.first }
    }

    /** Rank of [entry] for an already-normalized, non-empty query, or null when it doesn't match. */
    private fun rank(entry: AppEntry, q: String): Int? {
        val label = normalize(entry.label)
        return when {
            label.startsWith(q) -> RANK_PREFIX
            hasWordPrefix(label, q) -> RANK_WORD_PREFIX
            label.contains(q) -> RANK_SUBSTRING
            normalize(entry.packageName).contains(q) -> RANK_PACKAGE
            else -> null
        }
    }

    /** True if [q] occurs right after a non-alphanumeric character somewhere in [label]. */
    private fun hasWordPrefix(label: String, q: String): Boolean {
        var i = label.indexOf(q, startIndex = 1)
        while (i >= 0) {
            if (!label[i - 1].isLetterOrDigit()) return true
            i = label.indexOf(q, startIndex = i + 1)
        }
        return false
    }
}
