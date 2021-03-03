import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30"
}

group = "me.john"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
    maven ( "jcenter.bintray.com")
}

dependencies {
    testImplementation(kotlin("test-junit"))
    compile("com.github.kittinunf.fuel:fuel:2.3.1")
    compile("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    //compile("khttp:khttp:1.0.0")
    //compile("jline:jline:2.14.2")
    // Use JLine3 command-line library
    implementation("org.jline:jline-builtins:3.11.0")
    implementation("org.jline:jline-reader:3.11.0")
    implementation("org.jline:jline-terminal:3.11.0")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.example.MainKt"
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
}