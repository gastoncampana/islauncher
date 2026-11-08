package io.github.gastoncampana.launcher

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun writeShortcutIcon(file: File, encode: (OutputStream) -> Unit) {
    val temporary = Files.createTempFile(file.parentFile.toPath(), "shortcut-", ".png")
    try {
        Files.newOutputStream(temporary).use(encode)
        // A reader sees the old complete PNG or the new complete PNG, never a truncated encoding.
        Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
