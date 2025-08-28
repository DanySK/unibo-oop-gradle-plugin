package it.unibo.projecteval

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get

/**
 * This task removes all warning suppressions from the source code.
 */
open class RemoveSuppressions : DefaultTask() {
    /**
     * All java source files.
     */
    @OutputFiles
    val allSource: Set<File>

    init {
        val javaExtension = project.extensions.findByType(JavaPluginExtension::class.java)
        requireNotNull(javaExtension) { "Java plugin not found" }
        with(javaExtension) {
            allSource = (sourceSets["main"].allJava + sourceSets["test"].allJava).files
        }
    }

    /**
     * Removes all suppressions from the source code.
     */
    @TaskAction
    fun removeAllSuppressions() = allSource.asSequence()
        .filter { it.extension == "java" }
        .forEach { file ->
            var contents = file.readText()
            for (suppression in suppressions) {
                contents = contents.replace(suppression, "")
            }
            file.writeText(contents)
        }

    private companion object {
        private val suppressions: List<Regex> =
            listOf(
                Regex(
                    """(//+\s*)?@SuppressF?B?Warnings(\s*\(((\s*\w+\s*=\s*\".*?\"\s*,?)*|.*?)\))?\R?\s*((//.*?)?\R)?""",
                    RegexOption.DOT_MATCHES_ALL,
                ),
                Regex("""import(\s|\R)+edu\.umd\.cs\.findbugs\.annotations\.SuppressFBWarnings(\s|\R)*;"""),
                Regex("//\\s+NOPMD.*\$", RegexOption.MULTILINE),
                Regex("//\\s+CHECKSTYLE.*\$", RegexOption.MULTILINE),
            )
    }
}
