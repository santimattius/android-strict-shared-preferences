package com.santimattius.android.strict.preferences.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForbiddenFrameworkReflectionTest {
    @Test
    fun `main sources do not reflect on QueuedWork or SharedPreferencesImpl`() {
        val violations =
            mainSourceDirectory()
                .walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .mapNotNull { source ->
                    val sourceText = source.readText()
                    if (forbiddenFrameworkType.containsMatchIn(sourceText) && reflectionApi.containsMatchIn(sourceText)) {
                        source.relativeTo(mainSourceDirectory()).path
                    } else {
                        null
                    }
                }.toList()

        assertTrue(
            "Framework-private QueuedWork or SharedPreferencesImpl reflection is forbidden:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun mainSourceDirectory(): File =
        generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
            .map { File(it, "strict-preferences/src/main") }
            .firstOrNull(File::isDirectory)
            ?: error("Could not locate strict-preferences/src/main from ${System.getProperty("user.dir")}")

    private companion object {
        val forbiddenFrameworkType = Regex("""QueuedWork|SharedPreferencesImpl""")
        val reflectionApi = Regex("""Class\.forName|loadClass|get(?:Declared)?(?:Field|Method|Constructor)""")
    }
}
