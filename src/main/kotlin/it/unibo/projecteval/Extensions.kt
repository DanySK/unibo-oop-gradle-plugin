package it.unibo.projecteval

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import java.util.concurrent.TimeUnit

internal object Extensions {

    val authorMatch = Regex("^author\\s+(.+)$")

    fun NamedNodeMap.iterator() = object : Iterator<Node> {
        var index = 0
        override fun hasNext() = index < length
        override fun next() = item(index++)
    }

    operator fun Node.get(attribute: String, orElse: String): String = get(attribute) { orElse }

    operator fun Node.get(
        attribute: String,
        onFailure: () -> String = {
            throw IllegalArgumentException(
                "No attribute '$attribute' in $this. Available ones: ${attributes.iterator().asSequence().toList()}"
            )
        }
    ): String = attributes?.getNamedItem(attribute)?.textContent ?: onFailure()

    fun org.w3c.dom.NodeList.toIterable() = Iterable {
        object : Iterator<org.w3c.dom.Node> {
            var index = 0
            override fun hasNext(): Boolean = index < length - 1
            override fun next(): org.w3c.dom.Node = item(index++)
        }
    }

    fun Node.childrenNamed(name: String): List<Node> =
        childNodes.toIterable().filter { it.nodeName == name }

    fun String.endingWith(postfix: String): String = takeIf { endsWith(postfix) } ?: "$this$postfix"

    fun List<String>.commandOutput(): String = ProcessBuilder(this)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
        .apply { waitFor(1, TimeUnit.MINUTES) }
        .inputStream
        .bufferedReader()
        .readText()

    fun String.blameFor(lines: IntRange): Set<String> =
        listOf("git", "blame", "-L", "${lines.first},${lines.last}", "-p", this)
            .commandOutput()
            .lines()
            .flatMap { line -> authorMatch.matchEntire(line)?.destructured?.toList().orEmpty() }
            .toSet()
            .also {
                check(it.isNotEmpty()) {
                    "Unable to assign anything with: 'git blame -L ${lines.first},${lines.last} -p $this'"
                }
            }
}
