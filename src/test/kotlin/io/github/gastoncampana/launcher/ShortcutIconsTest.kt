package io.github.gastoncampana.launcher

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShortcutIconsTest {
    @get:Rule
    val files = TemporaryFolder(System.getenv("TMPDIR")?.let(::File))

    @Test
    fun aReaderKeepsThePreviousCompleteIconUntilEncodingFinishes() {
        val icon = files.newFile("icon.png").apply { writeText("previous icon") }
        writeShortcutIcon(icon) { out ->
            out.write("new".toByteArray())
            assertEquals("previous icon", icon.readText())
            out.write(" icon".toByteArray())
        }
        assertEquals("new icon", icon.readText())
        assertEquals(listOf("icon.png"), files.root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun failedEncodingPreservesTheOriginalIconAndLeavesNoTemporaryFile() {
        val icon = files.newFile("icon.png").apply { writeText("previous icon") }
        assertThrows(IOException::class.java) {
            writeShortcutIcon(icon) { out ->
                out.write("partial icon".toByteArray())
                throw IOException("Encoding failed")
            }
        }
        assertEquals("previous icon", icon.readText())
        assertEquals(listOf("icon.png"), files.root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun aNewIconBecomesVisibleOnlyAfterItIsComplete() {
        val icon = File(files.root, "icon.png")
        val bytes = byteArrayOf(1, 2, 3, 4)
        writeShortcutIcon(icon) { out ->
            out.write(bytes)
            assertFalse(icon.exists())
        }
        assertArrayEquals(bytes, icon.readBytes())
    }
}
