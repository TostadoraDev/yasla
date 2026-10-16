package xyz.tosdev.yetasimplelyicsapp

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Catch missing translations and format argument drift before Android falls back silently. */
class LocalizationResourcesTest {
    @Test
    fun englishAndSpanishCoverEveryInterfaceResourceAndKeepFormatArguments() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val english = resources(File(root, "values"))
        val spanish = resources(File(root, "values-es"))
        assertTrue("Full application interface should have translated resources", english.size > 100)
        assertEquals("Every base resource needs Spanish",
            english.keys.map { it.substringBefore('/') }.toSet(),
            spanish.keys.map { it.substringBefore('/') }.toSet())
        for ((name, value) in english) {
            assertTrue("Missing Spanish entry $name", name in spanish)
            assertEquals("Format arguments for $name", arguments(value), arguments(spanish.getValue(name)))
        }
        // Android selects plural categories by the target language. Spanish legitimately adds
        // `many` to English's one/other; it must keep the same arguments as the fallback form.
        for (name in spanish.keys - english.keys) {
            assertTrue("Unexpected translated entry $name", name.substringAfter('/') in
                setOf("zero", "one", "two", "few", "many", "other"))
            val fallback = "${name.substringBefore('/')}/other"
            assertTrue("Plural has no base fallback: $name", fallback in english)
            assertEquals("Format arguments for $name", arguments(english.getValue(fallback)),
                arguments(spanish.getValue(name)))
        }
    }

    @Test
    fun localeConfigExposesExactlyTheSupportedInterfaceLanguages() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val config = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "xml/locales_config.xml"))
        val locales = config.getElementsByTagName("locale")
        val names = (0 until locales.length).map { index ->
            (locales.item(index) as org.w3c.dom.Element)
                .getAttribute("android:name")
        }
        assertEquals(listOf("en", "es"), names)
    }

    private fun arguments(text: String) = Regex("%(?:\\d+\\$)?[dsf]").findAll(text)
        .map { it.value }.sorted().toList()

    private fun resources(directory: File): Map<String, String> = buildMap {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        directory.listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val doc = factory.newDocumentBuilder().parse(file)
            for (tag in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tag)
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index) as org.w3c.dom.Element
                    if (node.getAttribute("translatable") == "false") continue
                    val name = node.getAttribute("name")
                    if (tag == "string") put(name, node.textContent)
                    else {
                        val items = node.getElementsByTagName("item")
                        for (item in 0 until items.length) {
                            val entry = items.item(item) as org.w3c.dom.Element
                            val suffix = if (tag == "plurals") entry.getAttribute("quantity") else item.toString()
                            put("$name/$suffix", entry.textContent)
                        }
                    }
                }
            }
        }
    }
}
