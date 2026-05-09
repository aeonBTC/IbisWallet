package github.aeonbtc.ibiswallet.util

import java.util.Locale

/**
 * Minimal semver-style version parser/comparator for app and release tags.
 *
 * Supports:
 * - optional leading "v"
 * - numeric core versions like 3.1.3
 * - optional prerelease suffix like -beta or -rc.1
 * - optional build metadata like +123 (ignored for precedence)
 */
data class AppVersion(
    private val numericParts: List<Int>,
    private val prereleaseParts: List<PrereleasePart> = emptyList(),
) : Comparable<AppVersion> {
    val isStable: Boolean get() = prereleaseParts.isEmpty()

    override fun compareTo(other: AppVersion): Int {
        val maxPartCount = maxOf(numericParts.size, other.numericParts.size)
        for (index in 0 until maxPartCount) {
            val left = numericParts.getOrElse(index) { 0 }
            val right = other.numericParts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }

        if (isStable && other.isStable) return 0
        if (isStable) return 1
        if (other.isStable) return -1

        val maxPrereleaseCount = maxOf(prereleaseParts.size, other.prereleaseParts.size)
        for (index in 0 until maxPrereleaseCount) {
            val left = prereleaseParts.getOrNull(index)
            val right = other.prereleaseParts.getOrNull(index)

            if (left == null && right == null) return 0
            if (left == null) return -1
            if (right == null) return 1

            val partComparison = left.compareTo(right)
            if (partComparison != 0) return partComparison
        }

        return 0
    }

    companion object {
        private val VERSION_REGEX =
            Regex("""^\s*v?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?\s*$""")

        fun parse(raw: String?): AppVersion? {
            val match = VERSION_REGEX.matchEntire(raw.orEmpty()) ?: return null
            val numericParts =
                match.groupValues[1]
                    .split('.')
                    .mapNotNull { it.toIntOrNull() }

            if (numericParts.isEmpty()) return null

            val prereleaseParts =
                match.groupValues[2]
                    .takeIf { it.isNotBlank() }
                    ?.split('.')
                    ?.map(::parsePrereleasePart)
                    .orEmpty()

            return AppVersion(
                numericParts = numericParts,
                prereleaseParts = prereleaseParts,
            )
        }

        private fun parsePrereleasePart(raw: String): PrereleasePart {
            val numeric = raw.toIntOrNull()
            return if (numeric != null) {
                PrereleasePart.Numeric(numeric)
            } else {
                PrereleasePart.Text(raw.lowercase(Locale.US))
            }
        }
    }
}

sealed interface PrereleasePart : Comparable<PrereleasePart> {
    data class Numeric(val value: Int) : PrereleasePart

    data class Text(val value: String) : PrereleasePart

    override fun compareTo(other: PrereleasePart): Int =
        when {
            this is Numeric && other is Numeric -> value.compareTo(other.value)
            this is Numeric && other is Text -> -1
            this is Text && other is Numeric -> 1
            this is Text && other is Text -> value.compareTo(other.value)
            else -> 0
        }
}
