package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleSourcesTest {
    @Test fun findsTheSiteOfALink() {
        assertEquals(Trakt, TitleSources.of(" https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4 "))
        assertEquals(Simkl, TitleSources.of("https://simkl.com/tv/17465/game-of-thrones"))
        assertEquals(Letterboxd, TitleSources.of("https://letterboxd.com/film/the-matrix/"))
        assertEquals(Letterboxd, TitleSources.of("https://boxd.it/2bnu"))
        assertEquals(MdbList, TitleSources.of("https://mdblist.com/show/tt0903747"))
        assertEquals(Wetrakr, TitleSources.of("https://wetrakr.com/shows/1391953"))
    }

    @Test fun leavesImdbAndOtherLinksAlone() {
        assertNull(TitleSources.of("https://www.imdb.com/title/tt0120737/"))
        assertNull(TitleSources.of("https://www.youtube.com/watch?v=x"))
        assertNull(TitleSources.of("tt0120737"))
    }

    @Test fun aSharedSiteLinkIsLookedUpEvenWithAnImdbIdInIt() {
        assertEquals(
            Title.Shared("", "Breaking Bad", null, "https://mdblist.com/show/tt0903747"),
            Title.fromShared("https://mdblist.com/show/tt0903747", "Breaking Bad"),
        )
        assertEquals("tt0120737", Title.fromShared("https://www.imdb.com/title/tt0120737/", null)?.imdbId)
    }

    @Test fun searchesTheNameWhenNotFound() {
        assertEquals("the lord of the rings", Trakt.searchFor("https://trakt.tv/movies/the-lord-of-the-rings-2001"))
        assertEquals("game of thrones", Simkl.searchFor("https://simkl.com/tv/17465/game-of-thrones"))
        assertEquals("the gift", Letterboxd.searchFor("https://letterboxd.com/film/the-gift-2000/"))
        assertNull(Wetrakr.searchFor("https://wetrakr.com/shows/1391953"))
    }
}
