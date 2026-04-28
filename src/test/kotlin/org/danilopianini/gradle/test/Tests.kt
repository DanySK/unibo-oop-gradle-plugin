package org.danilopianini.gradle.test

import com.lordcodes.turtle.shellRun
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotBeEmpty
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempDirectory
import kotlin.io.path.useLines
import kotlin.text.RegexOption.MULTILINE
import kotlin.time.Duration.Companion.minutes
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class Tests :
    StringSpec({

        val organization = "unibo-oop-projects"
        val pluginsBlock = Regex("plugins\\s*\\{(.+?)}", RegexOption.DOT_MATCHES_ALL)
        fun normalizeJavaHome(path: String): String = File(path).canonicalFile.let { javaHomeDirectory ->
            if (javaHomeDirectory.name.equals("jre", ignoreCase = true)) {
                javaHomeDirectory.parentFile.canonicalPath
            } else {
                javaHomeDirectory.canonicalPath
            }
        }
        val javaHome = System.getenv("JAVA_HOME")
            ?.takeUnless { it.isBlank() }
            ?.let(::normalizeJavaHome)
            ?: normalizeJavaHome(System.getProperty("java.home"))
        val javaHomeForProperties = javaHome.replace('\\', '/')
        val lineSeparator = System.lineSeparator()
        val currentJavaFeature = Runtime.version().feature()
        val java17Toolchain = Regex("""JavaLanguageVersion\.of\(\s*17\s*\)""")
        val java17Version = Regex("""JavaVersion\.VERSION_17\b""")

        listOf(
            "OOP23-LucaFerar-Soprnzetti-Vdamianob-Velli-wulf",
            "OOP23-Azael-Fu-Jiaqi-Jiekai-Sun-Sun-ObjectMon",
            "OOP23-AlexGuerrini-AndreaSamori-DaviBart-MattiaRonchi-coloni-ces",
            "OOP24-AisjaBaglioni-BeatriceDiGregorio-Chiaradenardi-Fede-puyo-blast",
            "OOP24-Claudio-ClaudioLodi-LodiClaudio-LuigiLinari-Tbandini-TommasoBandini-TommasoGoni-Emberline",
            "OOP24-FrancescoSacripante-MatteoCaruso-MatteoCeccarelli-risikoop",
            "OOP25-FrancescoBarzanti-IreneBorri-RaffaelloFraboni-scot-yard",
        ).forEach { repository ->
            "test $repository" {
                timeout = 20.minutes.inWholeMilliseconds
                val destination: Path = createTempDirectory(repository)
                shellRun(
                    "git",
                    listOf(
                        "clone",
                        "https://github.com/$organization/$repository.git",
                        destination.absolutePathString(),
                    ),
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
                    ).replace(java17Toolchain, "JavaLanguageVersion.of($currentJavaFeature)")
                    .replace(java17Version, "JavaVersion.toVersion($currentJavaFeature)")
                val pluginsMatch = pluginsBlock.find(buildFileContent)
                checkNotNull(pluginsMatch)
                val newContent = buildFileContent.replaceRange(
                    pluginsMatch.range.last..pluginsMatch.range.last,
                    "    id(\"org.danilopianini.unibo-oop-gradle-plugin\")\n}",
                )
                buildFile.writeText(newContent)
                val gradleProperties = File(destination.toFile(), "gradle.properties")
                val gradleJavaHomeProperty = "org.gradle.java.home=$javaHomeForProperties"
                if (gradleProperties.exists() && gradleProperties.length() > 0L) {
                    val existingContent = gradleProperties.readText()
                    val gradleJavaHomeRegex = Regex("""^org\.gradle\.java\.home=.*$""", MULTILINE)
                    val updatedContent =
                        if (gradleJavaHomeRegex.containsMatchIn(existingContent)) {
                            existingContent.replace(gradleJavaHomeRegex, gradleJavaHomeProperty)
                        } else {
                            "$existingContent$lineSeparator$gradleJavaHomeProperty"
                        }
                    gradleProperties.writeText(updatedContent)
                } else {
                    gradleProperties.writeText(gradleJavaHomeProperty)
                }
                val destinationFile = destination.toFile()
                val result = with(GradleRunner.create()) {
                    withGradleVersion(
                        destination.resolve("gradle/wrapper/gradle-wrapper.properties").useLines { lines ->
                            lines.first { "services.gradle.org/distributions" in it }
                                .substringAfterLast("gradle-")
                                .substringBeforeLast("-")
                        },
                    )
                    withProjectDir(destinationFile)
                    withEnvironment(System.getenv() + ("JAVA_HOME" to javaHome))
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
