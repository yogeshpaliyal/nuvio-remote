package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Test

class StremioTest {
    @Test fun linksToAMoviesStreams() {
        val t = Title("tt0120737", "The Fellowship of the Ring", series = false)
        assertEquals("stremio:///detail/movie/tt0120737/tt0120737", t.stremioLink(streams = true))
        assertEquals("stremio:///detail/movie/tt0120737", t.stremioLink(streams = false))
    }

    @Test fun linksToAnEpisodesStreams() {
        val t = Title("tt42127457", "Agent Kim Reactivated", series = true, season = 1, episode = 4)
        assertEquals("stremio:///detail/series/tt42127457/tt42127457:1:4", t.stremioLink(streams = true))
    }

    @Test fun opensStremioWithItsLinkNotNuviosExtras() {
        val t = Title("tt42127457", "Agent Kim's", series = true, season = 1, episode = 4)
        assertEquals(
            "am start -n 'com.stremio.one/com.stremio.one.MainActivity' -f 0x14000000 " +
                "-a android.intent.action.VIEW -d 'stremio:///detail/series/tt42127457/tt42127457:1:4'",
            TvLink.command(t, "com.stremio.one/com.stremio.one.MainActivity", stremio = true),
        )
    }
}
