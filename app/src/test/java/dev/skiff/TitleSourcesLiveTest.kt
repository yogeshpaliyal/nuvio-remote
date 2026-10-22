package dev.skiff

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The sites themselves, over the internet: run with SKIFF_LIVE=1 (skipped otherwise, so builds don't need them). */
class TitleSourcesLiveTest {
    @Before fun live() = assumeTrue(System.getenv("SKIFF_LIVE") != null)

    private fun found(link: String) = runBlocking { TitleSources.of(link)!!.resolve(link) }

    @Test fun trakt() {
        assertEquals(TitleSource.Found(Title("tt0903747", "", true, 1, 4), true), found("https://trakt.tv/shows/breaking-bad/seasons/1/episodes/4"))
        assertEquals(TitleSource.Found(Title("tt0133093", "", false)), found("https://trakt.tv/movies/the-matrix-1999"))
    }

    @Test fun simkl() {
        assertEquals(TitleSource.Found(Title("tt0944947", "", true, 1, 4), true), found("https://simkl.com/tv/17465/game-of-thrones/season-1/episode-4"))
        assertEquals(TitleSource.Found(Title("tt0181852", "", false)), found("https://simkl.com/movies/53536/terminator-3-rise-of-the-machines"))
        assertEquals(TitleSource.Found(Title("tt2560140", "", true)), found("https://simkl.com/anime/39687/shingeki-no-kyojin"))
    }

    @Test fun letterboxd() {
        assertEquals(TitleSource.Found(Title("tt0133093", "", false)), found("https://letterboxd.com/film/the-matrix/"))
        assertEquals(TitleSource.Found(Title("tt0133093", "", false)), found("https://boxd.it/2a1m"))
    }

    @Test fun mdblist() {
        assertEquals(TitleSource.Found(Title("tt0903747", "", true)), found("https://mdblist.com/show/tt0903747"))
        assertEquals(TitleSource.Found(Title("tt11540908", "", true)), found("https://mdblist.com/show/3xzbl-carrie"))
    }

    @Test fun wetrakr() {
        assertEquals(TitleSource.Found(Title("tt0903747", "", true, 1, 4), true), found("https://wetrakr.com/shows/1391953/seasons/1/episodes/4"))
        assertEquals(TitleSource.Found(Title("tt0219699", "", false)), found("https://wetrakr.com/movies/1396"))
    }
}
