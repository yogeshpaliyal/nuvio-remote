package dev.skiff

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun comparesVersionsByNumberNotText() {
        assertTrue(Updates.newer("1.10.0", "1.9.2"))
        assertTrue(Updates.newer("1.9.0", "1.8.0"))
        assertTrue(Updates.newer("2.0", "1.99.99"))
        assertFalse(Updates.newer("1.8.0", "1.8.0"))
        assertFalse(Updates.newer("1.8.0", "1.8.1"))
        assertFalse(Updates.newer("1.8", "1.8.0"))
    }
}
