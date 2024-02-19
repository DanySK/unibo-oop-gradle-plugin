package org.danilopianini.gradle.test

import com.lordcodes.turtle.shellRun
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotBeEmpty
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.text.RegexOption.MULTILINE

class Tests : StringSpec({

    val organization = "unibo-oop-projects"
    val pluginsBlock = Regex("plugins\\s*\\{(.+?)}", RegexOption.DOT_MATCHES_ALL)

    listOf(
        "OOP22-Giamperoli-Sonofapo-jtrs",
        "OOP23-LucaFerar-Soprnzetti-Vdamianob-Velli-wulf",
    ).forEach { repository ->
        "test $repository" {
            val destination: Path = createTempDirectory(repository)
            shellRun(
                "git",
                listOf("clone", "https://github.com/$organization/$repository.git", destination.absolutePathString()),
            )
            val buildFileName = "build.gradle.kts"
            destination.shouldContainFile(buildFileName)
            destination.shouldContainFile("settings.gradle.kts")
            val buildFile = File(destination.toFile(), buildFileName)
            val buildFileContent = buildFile
                .readText()
                .replace(
                    Regex("""id\s*\(\s*"org\.danilopianini\.unibo-oop-gradle-plugin"\s*\).*$""", MULTILINE),
                    "",
                )
            val pluginsMatch = pluginsBlock.find(buildFileContent)
            checkNotNull(pluginsMatch)
            val newContent = buildFileContent.replaceRange(
                pluginsMatch.range.last..pluginsMatch.range.last,
                "    id(\"org.danilopianini.unibo-oop-gradle-plugin\")\n}",
            )
            buildFile.writeText(newContent)
            val destinationFile = destination.toFile()
            val result = with(GradleRunner.create()) {
                withProjectDir(destinationFile)
                withArguments("blame", "--stacktrace")
                withPluginClasspath()
                build()
            }
            requireNotNull(result.tasks.find { it.path == ":blame" }).outcome shouldBe TaskOutcome.SUCCESS
            val blame = File(destinationFile, "build/blame.md")
            blame.shouldExist()
            blame.shouldNotBeEmpty()
            destinationFile.deleteRecursively()
        }
    }
})
