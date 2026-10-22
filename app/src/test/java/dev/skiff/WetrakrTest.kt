package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WetrakrTest {
    @Test fun readsMoviesShowsAndEpisodes() {
        assertEquals(Wetrakr.Ref("603", series = false), Wetrakr.parse("https://wetrakr.com/movies/603"))
        assertEquals(Wetrakr.Ref("1391953", series = true), Wetrakr.parse("https://wetrakr.com/shows/1391953"))
        assertEquals(Wetrakr.Ref("1391953", true, 2), Wetrakr.parse("https://wetrakr.com/shows/1391953/seasons/2"))
        assertEquals(Wetrakr.Ref("1391953", true, 1, 4), Wetrakr.parse("https://wetrakr.com/shows/1391953/seasons/1/episodes/4"))
    }

    @Test fun leavesOtherPagesAlone() {
        assertNull(Wetrakr.parse("https://wetrakr.com/discover/shows"))
        assertNull(Wetrakr.parse("https://wetrakr.com/shows/trending"))
    }

    @Test fun findsTheImdbIdInTheTitlesData() {
        val json = """{"_id":"69bece","type":"show","ids":{"tmdb":{"id":1396},"imdb":{"id":"tt0903747"},"wikidata":{"id":"Q1079"}}}"""
        assertEquals("tt0903747", Wetrakr.imdbIdIn(json))
        assertNull(Wetrakr.imdbIdIn("""{"message":"Not found"}"""))
    }
}
