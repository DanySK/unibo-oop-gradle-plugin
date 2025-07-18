package it.unibo.projecteval

import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.konan.file.File
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList

internal object Extensions {
    val authorMatch = Regex("^author\\s+(.+)$")

    fun NamedNodeMap.iterator() = object : Iterator<Node> {
        var index = 0

        override fun hasNext() = index < length

        override fun next() = if (hasNext()) item(index++) else throw NoSuchElementException()
    }

    operator fun Node.get(attribute: String, orElse: String): String = get(attribute) { orElse }

    operator fun Node.get(
        attribute: String,
        onFailure: () -> String = {
            throw IllegalArgumentException(
                "No attribute '$attribute' in $this. Available ones: ${attributes.iterator().asSequence().toList()}",
            )
        },
    ): String = attributes?.getNamedItem(attribute)?.textContent ?: onFailure()

    fun NodeList.toIterable() = Iterable {
        object : Iterator<Node> {
            var index = 0

            override fun hasNext(): Boolean = index < length - 1

            override fun next(): Node = if (hasNext()) item(index++) else throw NoSuchElementException()
        }
    }

    fun Node.childrenNamed(name: String): List<Node> = childNodes.toIterable().filter { it.nodeName == name }

    fun String.endingWith(postfix: String): String = takeIf { endsWith(postfix) } ?: "$this$postfix"

    fun List<String>.commandOutput(): String = ProcessBuilder(this)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
        .apply { waitFor(1, TimeUnit.MINUTES) }
        .inputStream
        .bufferedReader()
        .readText()

    fun String.blameFor(lines: IntRange): Set<String> {
        val directory = this.substringBeforeLast(File.separator)
        val command = listOf("git", "-C", directory, "blame", "-L", "${lines.first},${lines.last}", "-p", this)
        val output = command.commandOutput()
        return output
            .lines()
            .flatMap { line ->
                authorMatch
                    .matchEntire(line)
                    ?.destructured
                    ?.toList()
                    .orEmpty()
            }.toSet()
            .also {
                check(it.isNotEmpty()) {
                    "Unable to assign anything with: '${command.joinToString(separator = " ")}':\n$output"
                }
            }
    }
}
