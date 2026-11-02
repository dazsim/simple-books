plugins {
    id("net.neoforged.moddev.legacyforge")
    id("neoforge-mutex")
}

val modVersion = providers.gradleProperty("version").orNull ?: sc.properties.get<String>("mod.version")
val mc = sc.current.version
version = modVersion
group = property("mod.group") as String
base.archivesName = "${property("mod.id")}-forge-$mc"

sourceSets.main {
    java.exclude("com/workshopcraft/simplebooks/fabric/**", "com/workshopcraft/simplebooks/neoforge/**")
    resources.setSrcDirs(listOf(rootProject.file("src/legacy/1.20.1"), rootProject.file("src/main/resources")))
}

legacyForge {
    version = property("deps.forge") as String
    accessTransformers.files.setFrom()
    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        register("client") {
            gameDirectory = rootProject.file("runs/${sc.current.project}")
            client()
            programArguments.addAll(providers.gradleProperty("quickPlay").map { listOf("--quickPlayMultiplayer", it) }.orElse(listOf()))
        }
        register("server") {
            gameDirectory = rootProject.file("runs/${sc.current.project}-server")
            server()
        }
    }
}

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")
}

java {
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    processResources {
        val props = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "version" to modVersion,
            "author" to sc.properties.get<String>("mod.author"),
            "description" to sc.properties.get<String>("mod.description"),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(props) }
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "simplebooks.accesswidener", "META-INF/accesstransformer.cfg",
            "assets/simplebooks/items/**", "data/simplebooks/recipe/**", "data/simplebooks/loot_table/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val name = project.property("mod.name")
        inputs.property("mod_name", name)
        from(rootProject.file("LICENSE")) { rename { "${it}_$name" } }
    }
}

apply(from = rootProject.file("gradle/dev-pack.gradle.kts"))
