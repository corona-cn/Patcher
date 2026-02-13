/* === PLUGIN === */
plugins {
    kotlin("jvm")
    `maven-publish`
}


/* === CONFIGURATION === */
kotlin {
    sourceSets {
        all {
            dependencies {
                /* Kotlin & Kotlinx */
                implementation(kotlin("stdlib"))
                implementation(kotlin("reflect"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            }
        }
    }
}


/* === JAR CONFIGURATION === */
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "patcher.core.PatcherEntry"
        )
    }

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("patcher")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
}


/* === OUTPUT COPY === */
tasks.register<Copy>("copyArtifact") {
    dependsOn("jar")
    from(tasks.jar.get().outputs.files)
    into(File(rootDir, "dist"))
    rename { "Patcher-${project.version}.jar" }
    doLast {
        println("Artifact copied to: ${File(rootDir, "dist/patcher-${project.version}.jar").absolutePath}")
    }
}

tasks.build {
    dependsOn("copyArtifact")
}