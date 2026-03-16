package com.shaoYe.reader

import java.io.File

fun main() {
    val html = EpubParser.getWelcomeHtml(false, 16)
    File("/home/jules/verification/welcome2.html").writeText(html)
}
