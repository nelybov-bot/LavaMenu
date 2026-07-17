plugins {
    id("dev.isxander.modstitch.base") version "0.8.4"
    id("java")
}

fun prop(name: String): String =
    findProperty(name)?.toString() ?: throw IllegalArgumentException("Missing property: $name")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

modstitch {
    minecraftVersion = prop("deps.mc")

    metadata {
        modId = prop("mod.id")
        modName = prop("mod.name")
        modVersion = prop("mod.version")
        modDescription = prop("mod.description")
        modGroup = "ru.lava"
        modAuthor = prop("mod.author")

        replacementProperties.put("mcdep", prop("mod.mcdep"))
    }

    loom {
        fabricLoaderVersion = prop("deps.fabric")
        configureLoom {
            // Оставляем дефолтные маппинги платформы (official/merged). Это влияет только на имена в исходниках,
            // на запуск мода в лаунчерах НЕ влияет.
        }
    }
}

dependencies {
    modstitchModImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric")}")
    // Минимум нужного для нашего мода
    modstitchModImplementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:4.0.5+c82f0461c3")
    modstitchModImplementation("net.fabricmc.fabric-api:fabric-key-mapping-api-v1:${prop("deps.keymap")}")
    modstitchModImplementation("net.fabricmc.fabric-api:fabric-message-api-v1:${prop("deps.msg")}")
}

tasks.processResources {
    // modstitch уже делает подстановки, но оставим и version для удобства
    inputs.property("version", prop("mod.version"))
    inputs.property("mcdep", prop("mod.mcdep"))
    inputs.property("mod_name", prop("mod.name"))
    inputs.property("mod_description", prop("mod.description"))
    filesMatching("fabric.mod.json") {
        expand(
            "version" to prop("mod.version"),
            "mcdep" to prop("mod.mcdep"),
            "mod_name" to prop("mod.name"),
            "mod_description" to prop("mod.description")
        )
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

