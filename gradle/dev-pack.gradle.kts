// dev only: the example books as a resource pack in this version's client run dir
val runDir = rootProject.file("runs/${project.name}")
val example = rootProject.file("dev/example-pack")
val format = when (project.name.substringBefore('-')) {
    "1.20.1" -> "\"pack_format\": 15"
    "1.21.1" -> "\"pack_format\": 34"
    else -> "\"min_format\": 84, \"max_format\": [200, 0]"
}
val devBookPack = tasks.register("devBookPack") {
    doLast {
        val pack = File(runDir, "resourcepacks/simplebooks-example")
        pack.deleteRecursively()
        example.copyRecursively(pack)
        File(pack, "pack.mcmeta").writeText("{ \"pack\": { \"description\": \"Simple Books example (dev only)\", $format } }\n")
        val opts = File(runDir, "options.txt")
        val lines = (if (opts.exists()) opts.readLines() else listOf()).filterNot { it.startsWith("resourcePacks:") } +
            "resourcePacks:[\"vanilla\",\"file/simplebooks-example\"]"
        opts.writeText(lines.joinToString("\n") + "\n")
    }
}
tasks.matching { it.name == "runClient" }.configureEach { dependsOn(devBookPack) }
