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

val useXCFramework: String by properties

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = libs.versions.jdk.get()
            }
        }
    }
    if (useXCFramework.toBooleanStrict()) {
        iosX64()
        iosSimulatorArm64()
    }
    iosArm64()


    applyDefaultHierarchyTemplate {
        common {
            group("mobile") {
                withIos()
                if (useXCFramework.toBooleanStrict()) {
                    withIosX64()
                    withIosSimulatorArm64()
                }
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
            isStatic = false
        }
        // todo
        /**
         * only change buildDir will cause the following error
         * ld: warning: ignoring file 'CocoapodsConflict/build/shared/cocoapods/framework/shared.framework/shared[2](shared.framework.o)':
         * found architecture 'arm64', required architecture 'x86_64'
         * ld: Undefined symbols:
         *   _OBJC_CLASS_$_SharedGreeting, referenced from:
         *        in ContentView.o
         * clang: error: linker command failed with exit code 1 (use -v to see invocation)
         *
         * modify the podspec file will cause the following error
         * ld: warning: search path '/Github/CocoapodsConflict/build/ios/Debug-iphonesimulator/XCFrameworkIntermediates/TXIMSDK_Plus_iOS_XCFramework' not found
         * ld: framework 'ImSDK_Plus' not found
         * clang: error: linker command failed with exit code 1 (use -v to see invocation)
         *
         * remove the pod every thing is ok
         */
        // TODO
        //TODO  😭😭😭 TXIMSDK_Plus_iOS 😭😭😭
        //  and
        //TODO  😭😭😭  TXIMSDK_Plus_iOS_XCFramework 😭😭😭
        //  only work for ios
        
        pod("TXIMSDK_Plus_iOS${if (useXCFramework.toBooleanStrict()) "_XCFramework" else ""}") {
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
kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java) {
    binaries.all {
        binaryOptions["memoryModel"] = "experimental"
    }
}



tasks.withType<PodGenTask>().configureEach {
    val podGen = this
    doLast {
        podfile.get().apply {
            val builder = CocoapodsAppender.Builder(this)
            // modify dependencies deployment target
            builder.deploymentTarget(podGen)

                .build()
        }
    }
}
tasks.withType<PodInstallTask>().configureEach {
    val podInstallTask = this
    doLast {
        podInstallTask.podspec.get().apply {
//            println("PodInstallTask podspec path $this")
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
                podBuilder.relinkGradle(project.projectDir, taskBuilder.podSpecDir)
                /* .excludeArch(listOf("x86_64", "i386"), rollback = !taskBuilder.isBuildDirChanged,
                     isPodspecType = true
                 )*/ // not work
            }
        }
        .build()

    CocoapodsAppender.Builder(rootDir.resolve("iosApp/Podfile"))
//        .excludeArch(listOf("x86_64", "i386"), rollback = !taskBuilder.isBuildDirChanged) // not work
        .rewriteSymroot(
            project.layout.buildDirectory.get().asFile,
            projectDir,
            rollback = !taskBuilder.isBuildDirChanged
        )
        .sharedPodRelink(
            taskBuilder.podSpecDir,
            !taskBuilder.isBuildDirChanged
        )?.apply {
            build()
        }

}
