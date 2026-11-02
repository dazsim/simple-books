import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

/** Drops the accessWidener line from fabric.mod.json for versions that ship without one. */
class StripAccessWidener(input: Reader) : FilterReader(
    StringReader(input.readText().lines().filterNot { it.contains("\"accessWidener\"") }.joinToString("\n"))
)
