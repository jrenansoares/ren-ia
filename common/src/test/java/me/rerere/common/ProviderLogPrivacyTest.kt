package me.rerere.common

import me.rerere.common.android.ContentFreeLogger
import org.junit.Assert.*
import org.junit.Test

class ProviderLogPrivacyTest {
    @Test fun payloadsCredentialsTagsAndExceptionsCannotLeakThroughProviderLogging() {
        val captured = mutableListOf<String>()
        val log = ContentFreeLogger { priority, tag, event -> captured.add("$priority $tag $event"); 0 }
        val content = "PERSONAL_CONVERSATION_CONTENT"
        val key = "API_KEY_MUST_NOT_LEAK"
        val failure = IllegalStateException("$key $content")
        log.v(key, content, failure)
        log.d(key, content, failure)
        log.i(key, content, failure)
        log.w(key, content, failure)
        log.e(key, content, failure)
        assertEquals(2, captured.size)
        assertFalse(captured.joinToString().contains(key))
        assertFalse(captured.joinToString().contains(content))
        assertFalse(captured.joinToString().contains("IllegalStateException"))
    }
}
