package it.unibo.projecteval

import it.unibo.projecteval.Extensions.childrenNamed
import it.unibo.projecteval.Extensions.get
import it.unibo.projecteval.Extensions.toIterable
import org.w3c.dom.Element
import java.io.File

/**
 * Extracts QA information from SpotBugs reports.
 */
class SpotBugsQAInfoExtractor(root: Element) : QAInfoContainer by (
    root.childNodes.let { childNodes ->
        val sourceDirs = childNodes.toIterable()
            .filter { it.nodeName == "Project" }
            .first()
            .childrenNamed("SrcDir")
            .map { it.textContent.trim() }
            .asSequence()
        childNodes.toIterable()
            .asSequence()
            .filter { it.nodeName == "BugInstance" }
            .mapNotNull { bugDescriptor ->
                val sourceLineDescriptor = bugDescriptor.childrenNamed("SourceLine").first()
                val category = bugDescriptor["category"].takeUnless { it == "STYLE" } ?: "UNSAFE"
                val startLine = sourceLineDescriptor["start", "1"].toInt()
                val endLine = sourceLineDescriptor["end", Integer.MAX_VALUE.toString()].toInt()
                val candidateFile = sourceLineDescriptor.get("relSourcepath") {
                    sourceLineDescriptor["sourcepath"]
                }
                val actualFile = sourceDirs.flatMap { File(it).walkTopDown() }
                    .map { it.absolutePath }
                    .first { candidateFile in it }
                actualFile.takeIf { it.isNotBlank() }?.let {
                    QAInfoForChecker(
                        "Potential bugs",
                        actualFile,
                        startLine..endLine,
                        "[$category] ${bugDescriptor.childrenNamed("LongMessage").first().textContent.trim()}",
                    )
                }
            }
            .asIterable()
    }
    )
