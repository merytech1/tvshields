package io.github.merytech1.tvshields.util

import java.io.File
import java.io.IOException

/** Replaces this file with [bytes] so readers never see it half written (temp file, then rename). */
internal fun File.writeAtomically(bytes: ByteArray) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(this)) {
        delete()
        if (!tmp.renameTo(this)) throw IOException("Cannot replace $this")
    }
}
