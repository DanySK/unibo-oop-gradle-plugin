package it.unibo.projecteval

import it.unibo.projecteval.Extensions.childrenNamed
import it.unibo.projecteval.Extensions.get
import it.unibo.projecteval.Extensions.toIterable

/**
 * Extracts QA information from PMD reports.
 */
class PmdQAInfoExtractor(
    root: org.w3c.dom.Element,
) : QAInfoContainer by (
        root.childNodes
            .toIterable()
            .asSequence()
            .filter { it.nodeName == "file" }
            .flatMap { file -> file.childrenNamed("violation").map { file to it } }
            .map { (file, violation) ->
                QAInfoForChecker(
                    "Sub-optimal Java object-orientation",
                    file["name"],
                    violation["beginline"].toInt()..violation["endline"].toInt(),
                    "[${violation["ruleset"].uppercase()}] ${violation.textContent.trim()}",
                )
            }.asIterable()
    )
