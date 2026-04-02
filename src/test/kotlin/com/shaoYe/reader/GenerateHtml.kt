package com.shaoYe.reader

import java.io.File

fun main() {
    val file = File("test-output.html")
    val html = EpubParser.getWelcomeHtml(isDarcula = false, fontSize = 16)
    file.writeText(html)
    println("Generated HTML at ${file.absolutePath}")
}
