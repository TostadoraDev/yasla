package xyz.tosdev.yetasimplelyicsapp.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderPriorityCodecTest {
    @Test
    fun roundTripPreservesOrderAndRemovesDuplicatesWhenReading() {
        val encoded = ProviderPriorityCodec.encode(listOf("local", "lrclib", "local"))
        assertEquals(listOf("local", "lrclib"), ProviderPriorityCodec.decode(encoded))
    }

    @Test
    fun emptyPreferenceProducesEmptyListForCallerFallback() {
        assertEquals(emptyList<String>(), ProviderPriorityCodec.decode(null))
    }
}
