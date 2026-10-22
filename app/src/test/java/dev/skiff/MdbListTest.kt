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

    @Test fun resolvesWithoutTheInternet() = runBlocking {
        assertEquals(TitleSource.Found(Title("tt0903747", "", series = true)), MdbList.resolve("https://mdblist.com/show/tt0903747"))
    }
}
