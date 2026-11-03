package com.scribidoo.app.util

import java.util.regex.Pattern

object UniqueNamingUtils {

    /**
     * Generates a unique document title given a list of existing titles.
     * If baseTitle is "Untitled Note", results will be "Untitled Note 1", "Untitled Note 2", etc.
     */
    fun generateUniqueDocumentTitle(
        existingTitles: List<String>,
        baseTitle: String = "Untitled Note"
    ): String {
        val pattern = Pattern.compile("^" + Pattern.quote(baseTitle) + "(?: (\\d+))?$")
        var maxIndex = 0
        var baseFound = false

        for (title in existingTitles) {
            val trimmed = title.trim()
            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                baseFound = true
                val numGroup = matcher.group(1)
                if (numGroup != null) {
                    val num = numGroup.toIntOrNull() ?: 0
                    if (num > maxIndex) maxIndex = num
                } else if (maxIndex == 0) {
                    maxIndex = 1
                }
            }
        }

        return if (!baseFound) {
            "$baseTitle 1"
        } else {
            "$baseTitle ${maxIndex + 1}"
        }
    }

    /**
     * Generates a unique title for a duplicated document.
     * E.g. "Meeting Notes" -> "Meeting Notes (Copy)" -> "Meeting Notes (Copy 2)".
     */
    fun generateDuplicateDocumentTitle(
        originalTitle: String,
        existingTitles: List<String>
    ): String {
        val cleanOriginal = originalTitle.trim()
        val firstCopy = "$cleanOriginal (Copy)"
        if (!existingTitles.contains(firstCopy)) {
            return firstCopy
        }

        val pattern = Pattern.compile("^" + Pattern.quote(cleanOriginal) + " \\(Copy (\\d+)\\)$")
        var maxIndex = 1

        for (title in existingTitles) {
            val matcher = pattern.matcher(title.trim())
            if (matcher.matches()) {
                val num = matcher.group(1)?.toIntOrNull() ?: 1
                if (num > maxIndex) maxIndex = num
            }
        }

        return "$cleanOriginal (Copy ${maxIndex + 1})"
    }

    /**
     * Generates a unique label for newly created or imported audio notes on a page or document.
     * E.g. "Voice Note 1", "Voice Note 2", etc.
     */
    fun generateUniqueAudioLabel(
        existingLabels: List<String>,
        baseLabel: String = "Voice Note"
    ): String {
        val pattern = Pattern.compile("^" + Pattern.quote(baseLabel) + "(?: (\\d+))?$")
        var maxIndex = 0

        for (label in existingLabels) {
            val matcher = pattern.matcher(label.trim())
            if (matcher.matches()) {
                val numGroup = matcher.group(1)
                val num = numGroup?.toIntOrNull() ?: 1
                if (num > maxIndex) maxIndex = num
            }
        }

        return "$baseLabel ${maxIndex + 1}"
    }
}
