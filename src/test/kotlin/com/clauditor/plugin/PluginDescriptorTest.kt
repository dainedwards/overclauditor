package com.clauditor.plugin

import com.intellij.openapi.components.Service
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Catches plugin.xml and service-registration mistakes that compile fine but leave a feature
 * dead at runtime (a misspelt class, a @Service annotation that landed on the wrong
 * declaration and left the Sessions pane empty).
 */
class PluginDescriptorTest {
    private val classAttributes = setOf("implementation", "implementationClass", "factoryClass", "instance", "class", "serviceImplementation")

    @Test
    fun `every class plugin xml names exists`() {
        val xml = javaClass.getResourceAsStream("/META-INF/plugin.xml") ?: fail("plugin.xml not on classpath")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml)
        val all = doc.getElementsByTagName("*")
        val missing = mutableListOf<String>()
        for (i in 0 until all.length) {
            val attrs = all.item(i).attributes ?: continue
            for (j in 0 until attrs.length) {
                val a = attrs.item(j)
                if (a.nodeName in classAttributes && a.nodeValue.startsWith("com.clauditor")) {
                    try { Class.forName(a.nodeValue.replace('$', '$'), false, javaClass.classLoader) } catch (_: ClassNotFoundException) { missing += a.nodeValue }
                }
            }
        }
        assertTrue(missing.isEmpty(), "plugin.xml names classes that don't exist: $missing")
    }

    @Test
    fun `every light service carries its @Service annotation`() {
        val services = listOf(
            "com.clauditor.services.ClaudeSessionService",
            "com.clauditor.services.ClaudeStatusService",
            "com.clauditor.services.ClaudeTerminalService",
            "com.clauditor.services.ClaudeUsageService",
            "com.clauditor.services.OpenSessionsPersistence",
            "com.clauditor.services.ClaudeContextService",
            "com.clauditor.services.ScratchpadService",
            "com.clauditor.settings.ClauditorSettings"
        )
        val unannotated = services.filter { Class.forName(it).getAnnotation(Service::class.java) == null }
        assertTrue(unannotated.isEmpty(), "missing @Service (getService would return null): $unannotated")
    }
}
