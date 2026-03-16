package com.shaoYe.reader

import java.io.File

fun main() {
    val file = File("/home/jules/verification/pride-and-prejudice.epub")
    try {
        val html = EpubParser.loadEpub(file, false, 16)
        File("/home/jules/verification/moby-dick.html").writeText(html)
    } catch (e: Exception) {
        println("Epub parser failed (EPUB v3 likely not supported by epublib). Attempting to load welcome instead...")
    }
}
