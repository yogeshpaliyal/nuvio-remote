package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoreTitleSourcesTest {
    @Test fun eachSiteIsFoundByItsLinks() {
        assertEquals(Simkl, TitleSources.of("https://simkl.com/tv/11121/breaking-bad"))
        assertEquals(Letterboxd, TitleSources.of("https://letterboxd.com/film/dune-part-two/"))
        assertEquals(Letterboxd, TitleSources.of("https://boxd.it/aBc1"))
        assertEquals(MdbList, TitleSources.of("https://mdblist.com/movie/2ygqn-hope"))
        assertEquals(WeTrakr, TitleSources.of("https://wetrakr.com/movies/882086"))
        assertEquals(Trakt, TitleSources.of("https://trakt.tv/movies/dune-2021"))
    }

    @Test fun otherPagesOfTheseSitesAreLeftAlone() {
        assertNull(TitleSources.of("https://simkl.com/tv/best-shows/most-voted/"))
        assertNull(TitleSources.of("https://letterboxd.com/someone/list/favourites/"))
        assertNull(TitleSources.of("https://letterboxd.com/films/popular/"))
        assertNull(TitleSources.of("https://mdblist.com/shows"))
        assertNull(TitleSources.of("https://wetrakr.com/discover/movies/trending"))
        assertNull(TitleSources.of("https://notsimkl.com/tv/11121/x"))
    }

    // Simkl

    @Test fun simklMovie() {
        assertEquals(
            TitleRef("https://simkl.com/movies/53536/dune-part-two", series = false, name = "dune part two"),
            Simkl.ref("https://simkl.com/movies/53536/dune-part-two?ref=share"),
        )
    }

    @Test fun simklShowAndEpisode() {
        assertEquals(
            TitleRef("https://simkl.com/tv/11121/breaking-bad", series = true, name = "breaking bad"),
            Simkl.ref("https://simkl.com/tv/11121/breaking-bad/"),
        )
        assertEquals(
            TitleRef("https://simkl.com/tv/11121/breaking-bad", true, season = 1, episode = 4, name = "breaking bad"),
            Simkl.ref("https://simkl.com/tv/11121/breaking-bad/season-1/episode-4/"),
        )
        assertEquals(
            TitleRef("https://simkl.com/tv/11121/breaking-bad", true, season = 5, name = "breaking bad"),
            Simkl.ref("https://simkl.com/tv/11121/breaking-bad/season-5/"),
        )
    }

    @Test fun simklAnimeWithoutSlug() {
        assertEquals(TitleRef("https://simkl.com/anime/40190", series = true), Simkl.ref("https://simkl.com/anime/40190"))
        assertNull(Simkl.searchFor("https://simkl.com/anime/40190"))
    }

    // Letterboxd

    @Test fun letterboxdFilm() {
        val dune = TitleRef("https://letterboxd.com/film/dune-2021/", series = false, name = "dune", year = "2021")
        assertEquals(dune, Letterboxd.ref("https://letterboxd.com/film/dune-2021/"))
        assertEquals(dune, Letterboxd.ref("https://m.letterboxd.com/film/dune-2021/reviews/"))
        assertEquals(dune, Letterboxd.ref("https://letterboxd.com/someone/film/dune-2021/"))
        assertEquals("dune", Letterboxd.searchFor("https://letterboxd.com/film/dune-2021/"))
    }

    @Test fun letterboxdShortLinkReadsThePage() {
        assertEquals(TitleRef("https://boxd.it/aBc1", series = false), Letterboxd.ref("https://boxd.it/aBc1"))
        assertNull(Letterboxd.ref("https://boxd.it/aBc1/extra"))
    }

    // MDBList

    @Test fun mdbListMovieAndShow() {
        assertEquals(
            TitleRef("https://mdblist.com/movie/2ygqn-hope", series = false, name = "hope"),
            MdbList.ref("https://mdblist.com/movie/2ygqn-hope"),
        )
        assertEquals(
            TitleRef("https://mdblist.com/show/1x2y3-breaking-bad", series = true, name = "breaking bad"),
            MdbList.ref("https://www.mdblist.com/show/1x2y3-breaking-bad?tab=info"),
        )
    }

    // WeTrakr

    @Test fun weTrakrMovieShowAndEpisode() {
        assertEquals(TitleRef("https://wetrakr.com/movies/882086", series = false), WeTrakr.ref("https://wetrakr.com/movies/882086"))
        assertEquals(TitleRef("https://wetrakr.com/shows/1396", series = true), WeTrakr.ref("https://wetrakr.com/shows/1396"))
        assertEquals(
            TitleRef("https://wetrakr.com/shows/1396", true, season = 1, episode = 4),
            WeTrakr.ref("https://wetrakr.com/shows/1396/seasons/1/episodes/4"),
        )
        // No name in the link: the page's title is searched.
        assertNull(WeTrakr.searchFor("https://wetrakr.com/movies/882086"))
    }

    // What the pages say

    @Test fun imdbIdFromAPage() {
        assertEquals("tt27369017", TitleSources.imdbIdIn("""<a class="imdb" href="https://www.imdb.com/title/tt27369017">IMDb</a>"""))
        assertEquals("tt0903747", TitleSources.imdbIdIn("""<a href="http://www.imdb.com/title/tt0903747/maindetails" data-track-action="IMDb">"""))
        assertNull(TitleSources.imdbIdIn("<html><a href=\"https://www.themoviedb.org/movie/882086\">TMDB</a></html>"))
    }

    @Test fun titleAndYearFromAPage() {
        assertEquals("Under the Grey Sky" to "2024", TitleSources.titleIn("<title>Under the Grey Sky (2024) · WeTrakr</title>"))
        assertEquals(
            "Schindler's List" to "1993",
            TitleSources.titleIn("""<meta property="og:title" content="Schindler&#039;s List (1993)" /><title>x</title>"""),
        )
        assertEquals("It's a Wonderful Life" to null, TitleSources.titleIn("""<meta property="og:title" content="It's a Wonderful Life">"""))
        assertEquals("Fast & Furious" to "2009", TitleSources.titleIn("<title>Fast &amp; Furious (2009) | MDBList</title>"))
        assertNull(TitleSources.titleIn("<html></html>"))
    }

    @Test fun slugWordsAndYear() {
        assertEquals("dune part two" to "2024", TitleSources.slugWords("dune-part-two-2024"))
        assertEquals("breaking bad" to null, TitleSources.slugWords("breaking-bad"))
        assertEquals("2001 a space odyssey" to null, TitleSources.slugWords("2001-a-space-odyssey"))
    }
}
