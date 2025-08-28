package it.unibo.projecteval

import com.github.spotbugs.snom.SpotBugsTask
import de.aaschmid.gradle.plugins.cpd.Cpd
import de.aaschmid.gradle.plugins.cpd.CpdExtension
import it.unibo.projecteval.Extensions.endingWith
import java.io.File
import kotlin.reflect.KClass
import org.danilopianini.javaqa.JavaQAPlugin
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType

private fun KClass<*>.simpleName(): String = requireNotNull(this.simpleName) { "Cannot get simple name for $this" }

private inline fun <T : Any> DomainObjectCollection<T>.configuration(crossinline action: T.() -> Unit) =
    configureEach { it.action() }

private val suffixMatches = Regex("extension|plugin")

private inline fun <reified T> Project.configureExtension(
    name: String = T::class.simpleName().lowercase().replace(suffixMatches, ""),
    crossinline action: T.() -> Unit,
) = extensions.getByName(name).let {
    when (it) {
        is T -> it.action()
        else -> error("Cannot configure $name: expected ${T::class.simpleName}, found ${it::class.simpleName}")
    }
}

private inline fun <reified T : Task> Project.registerTask(
    name: String,
    crossinline action: T.() -> Unit,
): TaskProvider<T> = tasks.register(name, T::class.java) { it.action() }

/**
 * Plugin for the OOP Project Evaluation.
 */
open class OOPProjectEvalPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            listOf(JavaPlugin::class, JavaQAPlugin::class).forEach {
                plugins.findPlugin(it.java) ?: plugins.apply(it.java)
            }
            tasks.withType<Test>().configuration {
                ignoreFailures = true
                useJUnitPlatform()
            }
            tasks.withType<SpotBugsTask>().configuration {
                ignoreFailures = true
                reports { it.create("xml") { enabled = true } }
            }
            configureExtension<PmdExtension> { isIgnoreFailures = true }
            configureExtension<CpdExtension> { isIgnoreFailures = true }
            configureExtension<CheckstyleExtension> { isIgnoreFailures = true }
            val removeSuppressions = tasks.register("removeSuppressions", RemoveSuppressions::class.java)
            tasks.withType<JavaCompile>().configureEach { it.dependsOn(removeSuppressions) }
            tasks.withType<Cpd> {
                reports {
                    it.xml.required.set(true)
                    it.text.required.set(true)
                }
                language = "java"
                minimumTokenCount = CPD_TOKENS
                ignoreFailures = true
                configureExtension<JavaPluginExtension> {
                    source = sourceSets["main"].allJava + sourceSets["test"].allJava
                }
            }
            registerTask<Task>("blame") {
                val dependencies = tasks.withType<Checkstyle>() +
                    tasks.withType<org.gradle.api.plugins.quality.Pmd>() +
                    tasks.withType<SpotBugsTask>() +
                    tasks.withType<Cpd>()
                dependsOn(dependencies)
                dependencies.forEach { it.dependsOn(removeSuppressions) }
                val identifier = if (project == rootProject) "" else "-${project.name}"
                val output = project.layout.buildDirectory.file("blame$identifier.md")
                outputs.file(output)
                doLast { _ ->
                    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    val xmlParser = factory.newDocumentBuilder()
                    val errors = dependencies
                        .flatMap { task ->
                            task.outputs.files.asIterable().filter { it.exists() && it.extension == "xml" }
                        }
                        .filter { it.exists() && it.length() > 0 }
                        .flatMap<File, QAInfo> {
                            val root: org.w3c.dom.Element = xmlParser.parse(it).documentElement
                            when (root.tagName) {
                                "pmd" -> PmdQAInfoExtractor(root)
                                "pmd-cpd" -> CpdQAInfoExtractor(root)
                                "checkstyle" -> CheckstyleQAInfoExtractor(root)
                                "BugCollection" -> SpotBugsQAInfoExtractor(root)
                                else -> emptyList<QAInfo>().also { println("Unknown root type ${root.tagName}") }
                            }
                        }
                        .distinct()
                    val errorsByStudentByChecker: Map<String, Map<String, List<QAInfo>>> = errors
                        .flatMap { error -> error.blamedTo.map { it to error } }
                        .groupBy { it.first }
                        .mapValues { (_, errors) -> errors.map { it.second }.groupBy { it.checker } }
                    val report = errorsByStudentByChecker.map { (student, errors) ->
                        """
                        |# $student
                        |
                        |${errors.map { it.value.size }.sum()} violations
                        |${errors.map { (checker, violations) ->
                            """
                            |## $checker: ${violations.size} mistakes
                            ${
                                violations.sortedBy { it.details }.joinToString("") {
                                    val fileName = File(it.file).name
                                    "|* ${it.details.endingWith(".")} In: $fileName@[${it.lines}]\n"
                                }.trimEnd()
                            }
                            """
                        }.joinToString(separator = "", prefix = "", postfix = "")}
                        |
                        """.trimMargin()
                    }.joinToString(separator = "", prefix = "", postfix = "")
                    file(output).writeText(report)
                }
            }
        }
    }

    private companion object {
        private const val CPD_TOKENS = 50
    }
}
