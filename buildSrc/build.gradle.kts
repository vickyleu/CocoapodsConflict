import java.util.Properties
if (System.getenv("JITPACK") == null) {
    val properties = Properties()
    properties.load(projectDir.parentFile.resolve("gradle.properties").inputStream())
    val changeBuildDir:String by properties
    if(changeBuildDir.toBooleanStrict()){
        rootProject.layout.buildDirectory.set(file("../build/buildSrc"))
    }
}
plugins {
    `kotlin-dsl`
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
kotlin {
    jvmToolchain(17)
}
repositories {
    google()
    mavenCentral()
}
