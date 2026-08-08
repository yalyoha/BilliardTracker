package com.example.billiardtracker.data.prefs

import com.example.billiardtracker.domain.rules.GameType
import org.junit.Assert.assertEquals
import org.junit.Test

class EnabledGameSlugsTest {
    @Test
    fun `ALL_SLUGS matches every GameType ruleFileSlug`() {
        val fromDomain = GameType.entries.map { it.ruleFileSlug }.toSet()
        assertEquals(fromDomain, UserPrefs.ALL_SLUGS)
    }
}
