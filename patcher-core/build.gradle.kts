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


/* === PRO GUARD === */
buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.8.2")
    }
}

val proguardTask = tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    dependsOn(tasks.jar)

    val originalJar = tasks.jar.get().outputs.files.singleFile
    val tempJar = layout.buildDirectory.file("libs/${project.name}-temp.jar")

    injars(originalJar)
    outjars(tempJar)

    libraryjars(mapOf("filter" to "!**.jar,!module-info.class"), System.getProperty("java.home") + "/jmods/java.base.jmod")
    libraryjars(mapOf("filter" to "!**.jar,!module-info.class"), System.getProperty("java.home") + "/jmods/java.desktop.jmod")
    libraryjars(mapOf("filter" to "!**.jar,!module-info.class"), System.getProperty("java.home") + "/jmods/java.sql.jmod")
    libraryjars(mapOf("filter" to "!**.jar,!module-info.class"), System.getProperty("java.home") + "/jmods/java.xml.jmod")
    configuration(files("proguard-rules.pro"))
    printseeds(layout.buildDirectory.file("proguard/seeds.txt"))
    printmapping(layout.buildDirectory.file("proguard/mapping.txt"))

    doLast {
        val finalJar = tasks.jar.get().outputs.files.singleFile
        if (tempJar.get().asFile.exists()) {
            if (finalJar.exists()) {
                finalJar.delete()
            }
            tempJar.get().asFile.renameTo(finalJar)
            println("ProGuard completed: ${finalJar.absolutePath}")
        }
    }
}

tasks.jar {
    finalizedBy(proguardTask)
}


/* === OUTPUT COPY === */
tasks.register<Copy>("copyArtifact") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().outputs.files)
    into(File(rootDir, "dist"))
    rename { "Patcher-${project.version}.jar" }
    doLast {
        println("Artifact copied to: ${File(rootDir, "dist/Patcher-${project.version}.jar").absolutePath}")
    }
}

tasks.build {
    dependsOn("copyArtifact")
}