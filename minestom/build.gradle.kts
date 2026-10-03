plugins {
    packetevents.`shadow-conventions`
    packetevents.`library-conventions`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.minestom)
    compileOnly(libs.jctools)
    shadow(project(":api", "shadow"))
}

configure<JavaPluginExtension> {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    withType<JavaCompile> {
        options.release = 25
    }
}
