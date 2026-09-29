package io.github.kjly.brna.storage

/**
 * What the workspace browser lists and in what order, after desktop Rnote's
 * (rnote-ui/src/workspacebrowser): folders first, then notes and the Xournal++ files
 * Rnote opens as notes, then the files a note can take in — PDFs and pictures — each
 * sorted the way people count, "Page 2" before "Page 10". Hidden files (a leading dot)
 * and everything else are left out.
 */
object FolderListing {

    enum class Kind { FOLDER, NOTE, XOPP, PDF, IMAGE }

    /** One thing in a folder. [id] is the storage provider's document id. */
    data class Entry(val id: String, val name: String, val kind: Kind, val lastModified: Long?)

    const val FOLDER_MIME = "vnd.android.document/directory"

    /** What [name] is, going by its type and then its extension; null for what isn't listed. */
    fun kindOf(name: String, mime: String?): Kind? {
        if (name.startsWith(".")) return null
        val lower = name.lowercase()
        return when {
            mime == FOLDER_MIME -> Kind.FOLDER
            lower.endsWith(".rnote") || mime == "application/rnote" -> Kind.NOTE
            lower.endsWith(".xopp") || mime == "application/x-xopp" -> Kind.XOPP
            lower.endsWith(".pdf") || mime == "application/pdf" -> Kind.PDF
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                mime == "image/png" || mime == "image/jpeg" -> Kind.IMAGE
            else -> null
        }
    }

    /** Folders, then notes, then PDFs and pictures together, each in [naturalCompare] order. */
    fun sorted(entries: List<Entry>): List<Entry> = entries.sortedWith(
        compareBy<Entry> {
            when (it.kind) {
                Kind.FOLDER -> 0
                Kind.NOTE, Kind.XOPP -> 1
                Kind.PDF, Kind.IMAGE -> 2
            }
        }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
    )

    /**
     * Rnote's "human numeric" order: runs of digits compare as numbers, everything else
     * letter by letter and regardless of case.
     */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val byDigits = numA.compareTo(numB)
                if (byDigits != 0) return byDigits
            } else {
                val byLetter = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (byLetter != 0) return byLetter
                i++
                j++
            }
        }
        val byLength = (a.length - i) - (b.length - j)
        return if (byLength != 0) byLength else a.compareTo(b)
    }

    /**
     * Rnote's name for a copy of [name]: "Notes - 1.rnote", or the next number free
     * among [taken]; a copy of a copy counts on from the original's name.
     */
    fun duplicateName(name: String, taken: Set<String>): String {
        val dot = name.lastIndexOf('.')
        val hasExtension = dot > 0
        val stem = if (hasExtension) name.substring(0, dot) else name
        val extension = if (hasExtension) name.substring(dot) else ""
        val base = stem.replace(DUP_SUFFIX, "")
        var n = 1
        while (true) {
            val candidate = "$base - $n$extension"
            if (candidate !in taken) return candidate
            n++
        }
    }

    /** [name] renamed to [newStem], keeping its extension: a note stays a note. */
    fun renamed(name: String, newStem: String): String {
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0) name.substring(dot) else ""
        val stem = newStem.trim()
        return if (extension.isNotEmpty() && stem.endsWith(extension, ignoreCase = true)) stem else stem + extension
    }

    /** Rnote's FILE_DUP_SUFFIX_DELIM_REGEX with the number after it, at the end of a stem. */
    private val DUP_SUFFIX = Regex("""\s-\s\d+$""")
}
