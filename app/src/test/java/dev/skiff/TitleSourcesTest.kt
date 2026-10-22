package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleSourcesTest {
    @Test fun findsTheSiteOfALink() {
        assertEquals(Trakt, TitleSources.of(" https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4 "))
    }

    @Test fun leavesImdbAndOtherLinksAlone() {
        assertNull(TitleSources.of("https://www.imdb.com/title/tt0120737/"))
        assertNull(TitleSources.of("https://www.youtube.com/watch?v=x"))
    }

    @Test fun traktSearchesTheNameWhenNotFound() {
        assertEquals("the lord of the rings", Trakt.searchFor("https://trakt.tv/movies/the-lord-of-the-rings-2001"))
    }
}
