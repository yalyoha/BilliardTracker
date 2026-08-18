package com.example.billiardtracker.ui.screens.tournament.scorers

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchLayoutTest {
    @Test fun `2 players → SplitVertical`() {
        assertEquals(TileLayout.SplitVertical, layoutFor(2))
    }
    @Test fun `3 players → Grid2x2`() {
        assertEquals(TileLayout.Grid2x2, layoutFor(3))
    }
    @Test fun `4 players → Grid2x2`() {
        assertEquals(TileLayout.Grid2x2, layoutFor(4))
    }
    @Test fun `5 players → VerticalList`() {
        assertEquals(TileLayout.VerticalList, layoutFor(5))
    }
    @Test fun `10 players → VerticalList`() {
        assertEquals(TileLayout.VerticalList, layoutFor(10))
    }
    @Test fun `1 player → VerticalList (edge case, degrade gracefully)`() {
        assertEquals(TileLayout.VerticalList, layoutFor(1))
    }
    @Test fun `0 players → VerticalList (empty state)`() {
        assertEquals(TileLayout.VerticalList, layoutFor(0))
    }
}
