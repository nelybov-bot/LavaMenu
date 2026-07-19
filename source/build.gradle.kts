plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("java")
}

fun prop(name: String): String =
    findProperty(name)?.toString() ?: throw IllegalArgumentException("Missing property: $name")

version = prop("mod.version")
group = prop("maven_group")
base.archivesName.set(prop("mod.id"))

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")

    // Минимум нужного для нашего мода
    implementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:${prop("deps.lifecycle")}")
    implementation("net.fabricmc.fabric-api:fabric-key-mapping-api-v1:${prop("deps.keymap")}")
    implementation("net.fabricmc.fabric-api:fabric-message-api-v1:${prop("deps.msg")}")
    implementation("net.fabricmc.fabric-api:fabric-rendering-v1:${prop("deps.render")}")
}

tasks.processResources {
    val version = prop("mod.version")
    val mcdep = prop("mod.mcdep")
    inputs.property("version", version)
    inputs.property("mcdep", mcdep)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand(
            "version" to version,
            "mcdep" to mcdep
        )
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${prop("mod.id")}" }
    }
}
