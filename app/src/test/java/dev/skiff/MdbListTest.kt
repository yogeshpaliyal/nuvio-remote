package dev.skiff

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MdbListTest {
    @Test fun readsTheIdAndTypeFromTheLink() {
        assertEquals(Title("tt0133093", "", series = false), MdbList.parse("https://mdblist.com/movie/tt0133093"))
        assertEquals(Title("tt0903747", "", series = true), MdbList.parse("https://mdblist.com/show/tt0903747?ref=x"))
    }

    @Test fun leavesListsAlone() {
        assertNull(MdbList.parse("https://mdblist.com/lists/someone/top-movies"))
        assertNull(MdbList.parse("https://mdblist.com/movie/the-matrix"))
    }

    @Test fun readsMdbListsOwnIds() {
        // As the site links its titles: the page says the IMDb id.
        assertEquals(MdbList.Ref("https://mdblist.com/show/3xzbl-carrie", series = true, slug = "carrie"), MdbList.ref("https://mdblist.com/show/3xzbl-carrie"))
        assertEquals(MdbList.Ref("https://mdblist.com/movie/2ygqn-hope", series = false, slug = "hope"), MdbList.ref("https://www.mdblist.com/movie/2ygqn-hope?tab=info"))
        assertEquals(MdbList.Ref("https://mdblist.com/movie/32uh4", series = false, slug = ""), MdbList.ref("https://mdblist.com/movie/32uh4"))
        assertEquals(MdbList, TitleSources.of("https://mdblist.com/show/3xzbl-carrie"))
        assertEquals("carrie", MdbList.searchFor("https://mdblist.com/show/3xzbl-carrie"))
        // An IMDb id's link isn't read as MDBList's id.
        assertNull(MdbList.ref("https://mdblist.com/show/tt0903747"))
    }

    @Test fun findsTheImdbIdOnAnMdbListPage() {
        val html = """<title>Carrie  (2026)  - mdblist.com</title><a href="https://www.imdb.com/title/tt11540908" target="_blank">IMDb</a>"""
        assertEquals("tt11540908", TitleSources.imdbLinkIn(html))
    }

    @Test fun resolvesWithoutTheInternet() = runBlocking {
        assertEquals(TitleSource.Found(Title("tt0903747", "", series = true)), MdbList.resolve("https://mdblist.com/show/tt0903747"))
    }
}
