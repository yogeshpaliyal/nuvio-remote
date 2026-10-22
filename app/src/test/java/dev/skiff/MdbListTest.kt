package dev.skiff

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MdbListTest {
    @Test fun readsTheIdAndTypeFromTheLink() {
        assertEquals(MdbList.Ref(series = false, imdbId = "tt0133093"), MdbList.parse("https://mdblist.com/movie/tt0133093"))
        assertEquals(MdbList.Ref(series = true, imdbId = "tt1582457"), MdbList.parse("https://www.mdblist.com/show/tt1582457?ref=x"))
    }

    @Test fun readsTheNameWhenTheLinkHasMdbListsOwnId() {
        assertEquals(MdbList.Ref(series = true, slug = "carrie"), MdbList.parse("https://mdblist.com/show/3xzbl-carrie"))
        assertEquals(MdbList.Ref(series = true, slug = "the-dinosaurs"), MdbList.parse("https://mdblist.com/show/44b2e-the-dinosaurs?cache=1"))
        assertEquals("the dinosaurs", MdbList.searchFor("https://mdblist.com/show/44b2e-the-dinosaurs?cache=1"))
    }

    @Test fun leavesListsAlone() {
        assertNull(MdbList.parse("https://mdblist.com/lists/someone/top-movies"))
        assertNull(MdbList.parse("https://mdblist.com/shows/?q_provider=634"))
    }

    @Test fun resolvesAnImdbLinkWithoutTheInternet() = runBlocking {
        assertEquals(TitleSource.Found(Title("tt0903747", "", series = true)), MdbList.resolve("https://mdblist.com/show/tt0903747"))
    }
}
