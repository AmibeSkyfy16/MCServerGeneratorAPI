val javaVersion = JavaVersion.VERSION_21

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("kapt") version "1.9.23"
    id("application")
}

base {
    archivesName.set(properties["archives_name"].toString())
    group = property("maven_group")!!
    version = property("mod_version")!!
}

repositories {
    mavenCentral()
    maven("https://repo.repsy.io/mvn/amibeskyfy16/repo")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("org.buildobjects:jproc:2.8.2")
    implementation("commons-io:commons-io:2.15.1")
    implementation("net.sourceforge.htmlunit:htmlunit:2.70.0")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.23")
}

tasks{


    configure<JavaPluginExtension>{
        println("default sourceCompatibility $sourceCompatibility")
//            sourceCompatibility = org.gradle.api.JavaVersion.VERSION_11
    }

    application {
        mainClass.set(property("mainClassPath")!!.toString())
    }

    named<Javadoc>("javadoc") {
        options {
            (this as CoreJavadocOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion.toString()))
            vendor.set(JvmVendorSpec.BELLSOFT)
        }
        withSourcesJar()
        withJavadocJar()
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion.toString()))
            vendor.set(JvmVendorSpec.BELLSOFT)
        }
    }

    // Why im using configureEach {} and not just {} -> https://blog.gradle.org/preview-avoiding-task-configuration-time
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = javaVersion.toString()
    }

    withType<JavaCompile>().configureEach {
        options.release.set(javaVersion.toString().toInt())
        options.compilerArgs.add("--enable-preview")
    }

    withType<JavaExec>().configureEach {
        this.jvmArgs("--enable-preview")
        standardInput = System.`in`
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs(listOf("--enable-preview"))

        testLogging {
            outputs.upToDateWhen { false } // When the build task is executed, stderr-stdout of test classes will be show
            showStandardStreams = true
        }
    }

    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        manifest {
            attributes(mapOf("Main-Class" to application.mainClass))
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        from(java.sourceSets.main.get().output)
    }

    create<Jar>("fatJar1") {

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveAppendix.set(this.taskIdentity.name)
        archiveClassifier.set("all")

        // Multiple way to set the manifest

        // #1
        manifest {
            attributes(mapOf("Main-Class" to application.mainClass))
        }
        // #2
        manifest {
            attributes["Main-Class"] = application.mainClass
        }
        // #3
        manifest {
            attributes("Main-Class" to application.mainClass)
        }

        from(java.sourceSets["main"].output) // #1
        from(java.sourceSets.main.get().output) // #2

        configurations.runtimeClasspath.get().filter {
            it.name.endsWith(".jar")
        }.forEach { jar ->
            println("add from dependencies: ${jar.name}")
            from(zipTree(jar))
        }

        java.sourceSets.main.get().allSource.forEach { println("add from sources: ${it.name}") }
    }

}