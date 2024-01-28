import java.util.Properties
if (System.getenv("JITPACK") == null) {
    val properties = Properties()
    projectDir.parentFile.resolve("gradle.properties").inputStream().use {
        // 需要过滤掉注释行
        it.bufferedReader().lines().filter { !it.startsWith("#") }.forEach {
            val split = it.split("=")
            if(split.size == 2){
                properties.setProperty(split[0].trim(), split[1].trim())
            }
        }
    }
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
