package it.unibo.projecteval

/**
 * A single issue detected by the static analyzer ([checker] and [blamedTo] to the committers.
 * The report includes the [file] where the problem was detected, the affected [lines] and the [details] of the issue.
 */
interface QAInfo {

    /**
     * The static analyzer producing the report.
     */
    val checker: String

    /**
     * The lines of code affected by the issue.
     */
    val lines: IntRange

    /**
     * A description of the issue.
     */
    val details: String

    /**
     * The file where the issue was detected.
     */
    val file: String

    /**
     * The committers responsible for the issue.
     */
    val blamedTo: Set<String>
}
