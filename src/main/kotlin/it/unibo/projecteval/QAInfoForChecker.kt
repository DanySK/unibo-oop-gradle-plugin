package it.unibo.projecteval

import it.unibo.projecteval.Extensions.blameFor
import java.io.File

/**
 * Scans textual reports of issue from static analyzers and attributes them to the committer.
 */
data class QAInfoForChecker(
    override val checker: String,
    override val file: String,
    override val lines: IntRange = 1..File(file).readText().lines().size,
    override val details: String = "",
    private val blamed: Set<String>? = null,
) : QAInfo {
    override val blamedTo: Set<String> = blamed ?: file.blameFor(lines)
}
