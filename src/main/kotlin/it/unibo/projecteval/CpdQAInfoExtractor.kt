package it.unibo.projecteval

import it.unibo.projecteval.Extensions.blameFor
import it.unibo.projecteval.Extensions.childrenNamed
import it.unibo.projecteval.Extensions.get
import it.unibo.projecteval.Extensions.toIterable
import org.w3c.dom.Element
import java.io.File

/**
 * Extracts QA information from CPD reports.
 */
class CpdQAInfoExtractor(
    root: Element,
) : QAInfoContainer by (
        root.childNodes
            .toIterable()
            .asSequence()
            .filter { it.nodeName == "duplication" }
            .map { duplication ->
                val files = duplication.childrenNamed("file")
                val filePaths = files.map { it["path"] }
                val lines = duplication["lines"].toInt()
                val shortFiles = files.map { "${File(it["path"]).name}:${it["line"]}" }
                val ranges =
                    files.map {
                        val begin = it["line"].toInt()
                        begin..(begin + lines)
                    }
                val blamed = filePaths.zip(ranges).flatMap { (file, lines) -> file.blameFor(lines) }.toSet()
                val description =
                    "Duplication of $lines lines" +
                        " and ${duplication["tokens"]} tokens across ${filePaths.toSet().size}" +
                        " files: ${shortFiles.joinToString(prefix = "", postfix = "")}"
                QAInfoForChecker(
                    "Duplications and violations of the DRY principle",
                    files.first()["path"],
                    ranges.first(),
                    description,
                    blamed,
                )
            }.asIterable()
    )
