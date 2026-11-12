package com.knightindustries.knighttraining.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {
    @Test
    fun theFirstLinkInSharedTextIsTaken() {
        assertEquals("https://youtu.be/HoPbWOhUq3I", ShareReceiverActivity.firstLink("Great endgame lecture https://youtu.be/HoPbWOhUq3I"))
        assertEquals("https://lichess.org/study/abc", ShareReceiverActivity.firstLink("(https://lichess.org/study/abc)."))
        assertEquals("http://example.com/a?b=c", ShareReceiverActivity.firstLink("http://example.com/a?b=c"))
    }

    @Test
    fun textWithoutALinkHasNone() {
        assertNull(ShareReceiverActivity.firstLink("just some words"))
    }
}
