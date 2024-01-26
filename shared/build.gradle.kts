@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.targets.native.tasks.PodGenTask
import org.jetbrains.kotlin.gradle.targets.native.tasks.PodInstallTask
import org.jetbrains.kotlin.gradle.tasks.PodspecTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidLibrary)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get()))
    }
}
kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = libs.versions.jdk.get()
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate {
        common {
            group("mobile") {
                withIos()
                withIosX64()
                withIosSimulatorArm64()
                withAndroidTarget()
            }
        }
    }
    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "16.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = true
        }
        // todo
        /**
         * ld: warning: ignoring file 'CocoapodsConflict/build/shared/cocoapods/framework/shared.framework/shared[2](shared.framework.o)':
         * found architecture 'arm64', required architecture 'x86_64'
         * ld: Undefined symbols:
         *   _OBJC_CLASS_$_SharedGreeting, referenced from:
         *        in ContentView.o
         * clang: error: linker command failed with exit code 1 (use -v to see invocation)
         *
         */
        pod("TXIMSDK_Plus_iOS_XCFramework") {
            version = "7.6.5021"
            packageName = "ImSDK_Plus"
            moduleName = "ImSDK_Plus"
            extraOpts = listOf(
                "-compiler-option", "-DNS_FORMAT_ARGUMENT(A)=",
                "-verbose"
            )
        }
    }
    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.example.cocoapodsconflict"
    compileSdk = 34
    defaultConfig {
        minSdk = 34
    }
}
tasks.withType<PodGenTask>().configureEach {
    val podGen = this
    doLast {
        podfile.get().apply {
            val builder = CocoapodsAppender.Builder(this)
            // modify dependencies deployment target
            builder.deploymentTarget(podGen).build()
        }
    }
}
tasks.withType<PodInstallTask>().configureEach {
    val podInstallTask = this
    doLast {
        podInstallTask.podspec.get().apply {
            println("PodInstallTask podspec path $this")
        }
    }
}
tasks.withType<PodspecTask>().configureEach {
    val podSpec = this
    val taskBuilder =
        CocoapodsAppender.TaskBuilder(
            podSpec,
            project.layout.buildDirectory.get().asFile,
            project.projectDir
        )
    taskBuilder
        .relinkPodspec()
        .withClosure { podBuilder ->
            if (taskBuilder.isBuildDirChanged) {
                podBuilder.relinkGradle(project.name)
            }
        }
        .build()

    val builder = CocoapodsAppender.Builder(rootDir.resolve("iosApp/Podfile"))

    builder.sharedPodRelink(!taskBuilder.isBuildDirChanged)?.apply {
        build()
    }

}
