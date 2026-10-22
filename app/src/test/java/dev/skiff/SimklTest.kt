package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimklTest {
    @Test fun readsMoviesShowsAndAnime() {
        assertEquals(Simkl.Ref("movies", "53536", "terminator-3-rise-of-the-machines"), Simkl.parse("https://simkl.com/movies/53536/terminator-3-rise-of-the-machines"))
        assertEquals(Simkl.Ref("tv", "17465", null), Simkl.parse("https://simkl.com/tv/17465"))
        assertEquals(Simkl.Ref("anime", "39687", "shingeki-no-kyojin"), Simkl.parse("https://www.simkl.com/anime/39687/shingeki-no-kyojin?ref=share"))
    }

    @Test fun readsAnEpisode() {
        assertEquals(
            Simkl.Ref("tv", "17465", "game-of-thrones", 1, 4),
            Simkl.parse("https://simkl.com/tv/17465/game-of-thrones/season-1/episode-4/"),
        )
    }

    @Test fun leavesOtherLinksAlone() {
        assertNull(Simkl.parse("https://simkl.com/search/?q=matrix"))
        assertNull(Simkl.parse("https://notsimkl.com/tv/17465"))
    }

    @Test fun readsTheApisAnswer() {
        val show = """{"title":"Game of Thrones","type":"show","ids":{"simkl":17465,"slug":"game-of-thrones","tvdb":"121361","imdb":"tt0944947","tmdb":"1399"}}"""
        assertEquals("tt0944947" to true, Simkl.read(show, "tv"))
        val movie = """{"title":"Terminator 3","type":"movie","ids":{"simkl":53536,"imdb":"tt0181852"}}"""
        assertEquals("tt0181852" to false, Simkl.read(movie, "movies"))
        val animeMovie = """{"title":"Your Name.","type":"anime","ids":{"imdb":"tt5311514"},"anime_type":"movie"}"""
        assertEquals("tt5311514" to false, Simkl.read(animeMovie, "anime"))
        val anime = """{"title":"Shingeki no Kyojin","type":"anime","ids":{"imdb":"tt2560140"},"anime_type":"tv","relations":[{"title":"Attack on Titan: Part 1","anime_type":"movie"}]}"""
        assertEquals("tt2560140" to true, Simkl.read(anime, "anime"))
        assertNull(Simkl.read("""{"title":"x","ids":{"simkl":1}}""", "tv"))
    }
}
