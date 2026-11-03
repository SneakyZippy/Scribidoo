package com.scribidoo.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueNamingUtilsTest {

    @Test
    fun testGenerateUniqueDocumentTitle_emptyList() {
        val result = UniqueNamingUtils.generateUniqueDocumentTitle(emptyList())
        assertEquals("Untitled Note 1", result)
    }

    @Test
    fun testGenerateUniqueDocumentTitle_existingItems() {
        val list = listOf("Untitled Note 1", "Untitled Note 2", "Personal Journal")
        val result = UniqueNamingUtils.generateUniqueDocumentTitle(list)
        assertEquals("Untitled Note 3", result)
    }

    @Test
    fun testGenerateUniqueDocumentTitle_gapInSequence() {
        val list = listOf("Untitled Note 1", "Untitled Note 5")
        val result = UniqueNamingUtils.generateUniqueDocumentTitle(list)
        assertEquals("Untitled Note 6", result)
    }

    @Test
    fun testGenerateDuplicateDocumentTitle_firstCopy() {
        val list = listOf("Project Plan")
        val result = UniqueNamingUtils.generateDuplicateDocumentTitle("Project Plan", list)
        assertEquals("Project Plan (Copy)", result)
    }

    @Test
    fun testGenerateDuplicateDocumentTitle_multipleCopies() {
        val list = listOf("Project Plan", "Project Plan (Copy)", "Project Plan (Copy 2)")
        val result = UniqueNamingUtils.generateDuplicateDocumentTitle("Project Plan", list)
        assertEquals("Project Plan (Copy 3)", result)
    }

    @Test
    fun testGenerateUniqueAudioLabel() {
        val emptyListResult = UniqueNamingUtils.generateUniqueAudioLabel(emptyList())
        assertEquals("Voice Note 1", emptyListResult)

        val list = listOf("Voice Note 1", "Voice Note 2")
        val result = UniqueNamingUtils.generateUniqueAudioLabel(list)
        assertEquals("Voice Note 3", result)
    }
}
