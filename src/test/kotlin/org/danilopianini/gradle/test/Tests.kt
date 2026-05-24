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
        val foojayResolverPlugin = Regex(
            """^\s*id\s*\(\s*"org\.gradle\.toolchains\.foojay-resolver-convention"\s*\)\s+version\s+".*"\s*$""",
            MULTILINE,
        )
        fun normalizeJavaHome(path: String): String? = runCatching {
            File(path)
                .takeIf { it.isDirectory }
                ?.canonicalFile
                ?.let { javaHomeDirectory ->
                    if (javaHomeDirectory.name.equals("jre", ignoreCase = true)) {
                        javaHomeDirectory.parentFile.canonicalPath
                    } else {
                        javaHomeDirectory.canonicalPath
                    }
                }
        }.getOrNull()
        fun javaExecutable(javaHome: String): String {
            val javaBinaryName =
                if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java"
            return File(File(javaHome, "bin"), javaBinaryName).absolutePath
        }
        val pluginJavaFeature = 21
        val javaHomeCandidates = sequenceOf(
            "JAVA_HOME_${pluginJavaFeature}_X64",
            "JAVA_HOME",
        ).mapNotNull { System.getenv(it)?.takeUnless(String::isBlank)?.let(::normalizeJavaHome) } +
            sequenceOf(
                normalizeJavaHome(System.getProperty("java.home")),
                System.getProperty("java.home"),
            ).mapNotNull { it }
        val javaHome = javaHomeCandidates.firstOrNull {
            Runtime.Version.parse(
                ProcessBuilder(javaExecutable(it), "-version")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .readText()
                    .lineSequence()
                    .first()
                    .substringAfter('"')
                    .substringBefore('"'),
            ).feature() >= pluginJavaFeature
        } ?: error("A JDK $pluginJavaFeature+ installation is required to execute TestKit builds")
        val javaHomeForProperties = javaHome.replace('\\', '/')
        val lineSeparator = System.lineSeparator()
        val javaLanguageVersionRegex = Regex("""JavaLanguageVersion\.of\(\s*\d+\s*\)""")
        val javaVersionEnumRegex = Regex("""JavaVersion\.VERSION_\w+\b""")
        val jvmToolchainVersionRegex = Regex("""((?:kotlin\.)?jvmToolchain)\(\s*\d+\s*\)""")

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
                val settingsFileName = "settings.gradle.kts"
                destination.shouldContainFile(buildFileName)
                destination.shouldContainFile(settingsFileName)
                val buildFile = File(destination.toFile(), buildFileName)
                val buildFileContent = buildFile
                    .readText()
                    .replace(
                        Regex("""id\s*\(\s*"org\.danilopianini\.unibo-oop-gradle-plugin"\s*\).*$""", MULTILINE),
                        "",
                    )
                    .replace(javaLanguageVersionRegex, "JavaLanguageVersion.of($pluginJavaFeature)")
                    .replace(javaVersionEnumRegex, "JavaVersion.toVersion($pluginJavaFeature)")
                    .replace(jvmToolchainVersionRegex) { "${it.groupValues[1]}($pluginJavaFeature)" }
                val pluginsMatch = pluginsBlock.find(buildFileContent)
                checkNotNull(pluginsMatch)
                val newContent = buildFileContent.replaceRange(
                    pluginsMatch.range.last..pluginsMatch.range.last,
                    "    id(\"org.danilopianini.unibo-oop-gradle-plugin\")\n}",
                )
                buildFile.writeText(newContent)
                val settingsFile = File(destination.toFile(), settingsFileName)
                settingsFile.writeText(settingsFile.readText().replace(foojayResolverPlugin, ""))
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
