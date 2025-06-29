plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.translatestrings"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

// Project information
ext {
    set("projectName", "Translate strings")
    set("authorName", "Thanh Nguyen")
    set("authorEmail", "thanhnguyen6702@gmail.com")
    set("projectUrl", "https://github.com/thanhnguyen6702/translate-strings")
}

intellij {
    version.set("2023.2")
    type.set("IC")
    plugins.set(emptyList())
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("")
    }

    buildSearchableOptions {
        enabled = false
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
