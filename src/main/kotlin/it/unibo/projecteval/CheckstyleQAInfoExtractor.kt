package it.unibo.projecteval

import it.unibo.projecteval.Extensions.childrenNamed
import it.unibo.projecteval.Extensions.get
import it.unibo.projecteval.Extensions.toIterable

/**
 * Extracts QA information from Checkstyle reports.
 */
class CheckstyleQAInfoExtractor(
    root: org.w3c.dom.Element,
) : QAInfoContainer by (
        root.childNodes
            .toIterable()
            .asSequence()
            .filter { it.nodeName == "file" }
            .flatMap { file -> file.childrenNamed("error").map { file["name"] to it } }
            .map { (file, error) ->
                val line = error["line"].toInt()
                val lineRange = line..line
                QAInfoForChecker("Style errors", file, lineRange, error["message"])
            }.asIterable()
    )
