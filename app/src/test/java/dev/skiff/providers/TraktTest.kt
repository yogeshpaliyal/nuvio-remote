package dev.skiff.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TraktTest {
    @Test fun readsAMovieLink() {
        assertEquals(
            Trakt.Ref("the-lord-of-the-rings-the-fellowship-of-the-ring-2001", series = false),
            Trakt.parse("https://trakt.tv/movies/the-lord-of-the-rings-the-fellowship-of-the-ring-2001?utm_source=share"),
        )
    }

    @Test fun readsAShowAndItsEpisode() {
        assertEquals(Trakt.Ref("breaking-bad", series = true), Trakt.parse("https://trakt.tv/shows/breaking-bad"))
        assertEquals(Trakt.Ref("breaking-bad", true, 2), Trakt.parse("https://app.trakt.tv/shows/breaking-bad/seasons/2"))
        assertEquals(Trakt.Ref("breaking-bad", true, 1, 4), Trakt.parse("https://www.trakt.tv/shows/breaking-bad/seasons/1/episodes/4"))
    }

    @Test fun leavesOtherLinksAlone() {
        assertNull(Trakt.parse("https://www.imdb.com/title/tt0120737/"))
        assertNull(Trakt.parse("https://trakt.tv/users/someone/history"))
        assertNull(Trakt.parse("https://nottrakt.tv/movies/x"))
    }

    @Test fun isTheProviderForItsLinks() {
        assertSame(Trakt, Providers.forLink(" https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4 "))
        assertNull(Providers.forLink("https://www.imdb.com/title/tt0120737/"))
        assertNull(Providers.forLink(null))
    }

    @Test fun findsTheImdbIdOnThePage() {
        val html = """<a href="https://trakt.tv/movies/x/watch">Watch</a><a id="external-link-imdb" href="https://www.imdb.com/title/tt0120737/">IMDB</a>"""
        assertEquals("tt0120737", Providers.imdbIdIn(html))
        assertNull(Providers.imdbIdIn("<html>No links</html>"))
    }

    @Test fun nameAndYearFromTheSlug() {
        assertEquals("the lord of the rings" to "2001", Trakt.nameAndYear("the-lord-of-the-rings-2001"))
        assertEquals("breaking bad" to null, Trakt.nameAndYear("breaking-bad"))
        assertEquals("breaking bad", Trakt.searchWords("https://trakt.tv/shows/breaking-bad/seasons/1"))
        assertNull(Trakt.searchWords("https://trakt.tv/movies/120"))
    }
}
