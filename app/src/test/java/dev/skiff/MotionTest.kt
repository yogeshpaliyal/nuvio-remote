package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test fun takesWholeCountsAndKeepsTheFractions() {
        val m = Motion()
        m.move(1.6f, -0.7f)
        m.turn(0.5f)
        assertEquals(Triple(1, 0, 0), m.take())
        m.move(0.5f, -0.4f)
        m.turn(0.6f)
        // 0.6 + 0.5 and -0.7 - 0.4 and 0.5 + 0.6 make whole counts now.
        assertEquals(Triple(1, -1, 1), m.take())
        assertEquals(Triple(0, 0, 0), m.take())
    }
}
