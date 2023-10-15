plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.kyori.indra.licenser.spotless") version "3.1.3"
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
        content {
            includeGroup("org.vineflower")
        }
    }
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation("com.squareup.moshi:moshi:1.14.0")
    implementation("info.picocli:picocli:4.7.5")
    implementation("org.vineflower:vineflower:1.10.0-SNAPSHOT")
    implementation("net.neoforged:DiffPatch:2.0.14")
    implementation("net.fabricmc:tiny-remapper:0.8.10")
}

indraSpotlessLicenser {
    licenseHeaderFile("HEADER.txt")
    newLine(true)
}

tasks {
    jar {
        manifest {
            attributes("Main-Class" to "juuxel.mccmp.Mccmp")
        }
    }
}
