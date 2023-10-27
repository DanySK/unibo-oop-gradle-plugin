package org.danilopianini.gradle.test

import com.lordcodes.turtle.shellRun
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory

class Tests : StringSpec({

    val organization = "unibo-oop-projects"
    val pluginsBlock = Regex("plugins\\s*\\{(.+?)}", RegexOption.DOT_MATCHES_ALL)

    listOf("OOP22-Giamperoli-Sonofapo-jtrs").forEach { repository ->
        "test $repository" {
            val destination = createTempDirectory(repository)
            shellRun(
                "git",
                listOf("clone", "https://github.com/$organization/$repository.git", destination.absolutePathString()),
            )
            val buildFileName = "build.gradle.kts"
            destination.shouldContainFile(buildFileName)
            destination.shouldContainFile("settings.gradle.kts")
            val buildFile = File(destination.toFile(), buildFileName)
            val buildFileContent = buildFile.readText()
            val pluginsMatch = pluginsBlock.find(buildFileContent)
            check(pluginsMatch != null)
            val newContent = buildFileContent.replaceRange(
                pluginsMatch.range.last..pluginsMatch.range.last,
                "    id(\"org.danilopianini.unibo-oop-gradle-plugin\")\n}",
            )//.replace("id(\"org.danilopianini.gradle-java-qa\") version \"1.9.0\"", "")
            buildFile.writeText(newContent)
            val asd = Thread.currentThread()
                .contextClassLoader
                .getResource("org/danilopianini/javaqa/versions.properties")!!
            println(asd.path)
            val result = with(GradleRunner.create()) {
                withProjectDir(destination.toFile())
                withArguments("blame", "--stacktrace")
                withPluginClasspath()
                build()
            }
            requireNotNull(result.tasks.find { it.path == ":blame" }) shouldBe TaskOutcome.SUCCESS
        }
    }
})
