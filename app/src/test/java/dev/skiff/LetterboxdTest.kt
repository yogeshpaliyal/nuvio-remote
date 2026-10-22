package dev.skiff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterboxdTest {
    @Test fun readsAFilmsLink() {
        assertEquals("the-matrix", Letterboxd.slug("https://letterboxd.com/film/the-matrix/"))
        assertEquals("the-matrix", Letterboxd.slug("https://letterboxd.com/film/the-matrix/reviews/by/activity/"))
        assertEquals("the-gift-2000", Letterboxd.slug("https://letterboxd.com/someone/film/the-gift-2000/"))
    }

    @Test fun takesShortLinks() {
        assertTrue(Letterboxd.handles("https://boxd.it/2bnu"))
        assertNull(Letterboxd.slug("https://boxd.it/2bnu"))
    }

    @Test fun leavesOtherPagesAlone() {
        assertFalse(Letterboxd.handles("https://letterboxd.com/films/popular/"))
        assertFalse(Letterboxd.handles("https://letterboxd.com/someone/"))
        assertFalse(Letterboxd.handles("https://letterboxd.com/someone/list/favourites/"))
    }

    @Test fun findsTheImdbIdOnThePage() {
        val html = """<p class="text-link text-footer">More at <a href="http://www.imdb.com/title/tt0133093/maindetails" class="micro-button track-event" data-track-action="IMDb">IMDb</a>"""
        assertEquals("tt0133093", TitleSources.imdbLinkIn(html))
    }
}
