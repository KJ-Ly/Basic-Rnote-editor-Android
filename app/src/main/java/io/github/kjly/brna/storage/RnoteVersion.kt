package io.github.kjly.brna.storage

/**
 * Which Rnote file versions this app knows the format of. An `.rnote` names the version
 * that wrote it (`RnotefileWrapper::version`), and Rnote changes its format between minor
 * versions — 0.15 moved every position to a new layout (see [RnoteAffines]). A file from a
 * version newer than [NEWEST_KNOWN_MINOR] may hold what this app would lose or misplace
 * by reading and writing it back, so such a note is shown but never saved over.
 */
object RnoteVersion {

    /** The newest Rnote minor version whose format this app reads: 0.15. */
    const val NEWEST_KNOWN_MINOR = 15

    /**
     * Whether [version], as a file names it — semver, "0.16.0" or "0.16.0-dev" — is newer
     * than any this app knows. A file naming none, or none that reads as a version, is
     * not: older files and this app's own don't need protecting from it.
     */
    fun isNewerThanKnown(version: String?): Boolean {
        val parts = version?.trim()?.substringBefore('-')?.substringBefore('+')?.split('.') ?: return false
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        return major > 0 || minor > NEWEST_KNOWN_MINOR
    }
}
