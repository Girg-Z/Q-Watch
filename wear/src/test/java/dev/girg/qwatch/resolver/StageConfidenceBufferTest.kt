package dev.girg.qwatch.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StageConfidenceBufferTest {

    @Test
    fun `no commit until a majority is reached`() {
        val buffer = StageConfidenceBuffer(capacity = 5, majority = 3)
        assertNull(buffer.add("red"))
        assertNull(buffer.add("red"))
        assertEquals("red", buffer.add("red"))
    }

    @Test
    fun `a single stray fix does not flip a committed stage`() {
        val buffer = StageConfidenceBuffer(capacity = 5, majority = 3)
        buffer.add("red"); buffer.add("red"); buffer.add("red") // committed = red
        // One stray "blue" — red is still the majority of the last 5.
        assertEquals("red", buffer.add("blue"))
    }

    @Test
    fun `commit changes once the new value holds the majority`() {
        val buffer = StageConfidenceBuffer(capacity = 5, majority = 3)
        buffer.add("red"); buffer.add("red"); buffer.add("red") // committed = red
        buffer.add("blue") // [red,red,red,blue] -> red still majority
        buffer.add("blue") // [red,red,red,blue,blue] -> red 3 vs blue 2, unchanged
        assertEquals("red", buffer.committed())
        assertEquals("blue", buffer.add("blue")) // [red,red,blue,blue,blue] -> blue majority
    }

    @Test
    fun `between sentinel commits like any other key`() {
        val buffer = StageConfidenceBuffer(capacity = 5, majority = 3)
        buffer.add(KEY_BETWEEN); buffer.add(KEY_BETWEEN)
        assertEquals(KEY_BETWEEN, buffer.add(KEY_BETWEEN))
    }

    @Test
    fun `reset clears the committed value`() {
        val buffer = StageConfidenceBuffer(capacity = 5, majority = 3)
        buffer.add("red"); buffer.add("red"); buffer.add("red")
        buffer.reset()
        assertNull(buffer.committed())
        assertNull(buffer.add("red"))
    }
}
