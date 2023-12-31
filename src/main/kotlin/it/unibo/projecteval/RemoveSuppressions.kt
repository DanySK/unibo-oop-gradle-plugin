package it.unibo.projecteval

import org.gradle.api.DefaultTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.get
import java.io.File

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

    companion object {
        private val suppressions: List<Regex> = listOf(
            "@SuppressF?B?Warnings(\\s*\\(.*?\\)\\s*)?",
            "//\\s+NOPMD.*\$",
            "//\\s+CHECKSTYLE.*\$",
        ).map { it.toRegex() }
    }
}
