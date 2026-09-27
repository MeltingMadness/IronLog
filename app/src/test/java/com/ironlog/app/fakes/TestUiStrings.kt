package com.ironlog.app.fakes

import com.ironlog.app.presentation.common.UiStrings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Reads the real default copy for JVM tests, without an Android Context or duplicated text. */
object TestUiStrings : UiStrings {
    private val values: Map<Int, String> by lazy {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val modules = listOf("core/designsystem") + File(root, "feature").listFiles()
            .orEmpty().filter { it.isDirectory }.map { "feature/${it.name}" }
        buildMap {
            val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            for (module in modules) {
                val resourceClass = Class.forName("com.ironlog.${module.replace('/', '.')}.R\$string")
                val directory = File(root, "$module/src/main/res/values")
                for (file in directory.listFiles().orEmpty().filter { it.extension == "xml" }) {
                    val nodes = builder.parse(file).getElementsByTagName("string")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index) as Element
                        val id = resourceClass.getField(node.getAttribute("name")).getInt(null)
                        put(id, node.textContent.replace("\\'", "'").replace("\\n", "\n"))
                    }
                }
            }
        }
    }

    override fun get(resourceId: Int, vararg args: Any): String {
        val template = checkNotNull(values[resourceId]) { "Missing string resource $resourceId" }
        return if (args.isEmpty()) template else String.format(Locale.GERMANY, template, *args)
    }
}
