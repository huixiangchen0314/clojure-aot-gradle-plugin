package top.kzre.clojureaotgradleplugin

class Classifier(config: PureJavaConfig) {
    private val excludes: MutableList<String> =
        config.excludes.getOrElse(emptyList()).toMutableList()
    private val includes: MutableList<String> =
        config.includes.getOrElse(emptyList()).toMutableList()
    private val defaultPure: Boolean =
        config.defaultMode.getOrElse("impure").equals("pure", ignoreCase = true)

    fun isPure(qualifiedName: String): Boolean {
        if (excludes.any { matchPattern(it, qualifiedName) }) return false
        if (includes.any { matchPattern(it, qualifiedName) }) return true
        return defaultPure
    }

    private fun matchPattern(pattern: String, name: String): Boolean {
        return if (pattern.contains('*')) {
            matchGlob(pattern, name)
        } else {
            pattern == name
        }
    }

    private fun matchGlob(pattern: String, name: String): Boolean {
        return matchSegments(pattern.split('.'), name.split('.'), 0, 0)
    }

    private fun matchSegments(
        pat: List<String>,
        name: List<String>,
        pIdx: Int,
        nIdx: Int
    ): Boolean {
        if (pIdx == pat.size && nIdx == name.size) return true
        if (pIdx == pat.size) return false
        if (nIdx == name.size) {
            return (pIdx until pat.size).all { pat[it] == "**" }
        }
        return when (val p = pat[pIdx]) {
            "**" -> matchSegments(pat, name, pIdx + 1, nIdx) ||
                    matchSegments(pat, name, pIdx, nIdx + 1)
            "*"  -> matchSegments(pat, name, pIdx + 1, nIdx + 1)
            else -> p == name[nIdx] && matchSegments(pat, name, pIdx + 1, nIdx + 1)
        }
    }
}