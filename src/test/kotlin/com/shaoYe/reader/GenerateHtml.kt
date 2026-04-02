import java.io.File
import com.shaoYe.reader.EpubParser

fun main() {
    val parser = EpubParser

    // We will generate the welcome HTML since it showcases the typography and layout changes.
    // The loadEpub method requires a valid EPUB file which we might not have on hand.
    val welcomeHtml = parser.getWelcomeHtml(isDarcula = false, fontSize = 16)

    val file = File("/home/jules/verification/welcome.html")
    file.writeText(welcomeHtml)
    println("Successfully generated welcome.html at ${file.absolutePath}")
}
