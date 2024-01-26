//需要判断是否是jitpack的构建，如果是jitpack的构建，需要将build目录设置到项目根目录下

val changeBuildDir:String by properties

if (System.getenv("JITPACK") == null) {
    if(changeBuildDir.toBooleanStrict()){
        rootProject.layout.buildDirectory.set(file("./build"))
    }
}
plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.kotlinCocoapods).apply(false)
}
val buildRoot: String = rootProject.layout.buildDirectory.get().asFile.absolutePath
allprojects {
    if (System.getenv("JITPACK") == null) {
        if(changeBuildDir.toBooleanStrict()){
            val buildPath = if (this == rootProject) {
                file(buildRoot)
            } else {
                file("${buildRoot}/${this.name}")
            }
            this.layout.buildDirectory.set(buildPath)
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory.get().asFile)
    rootProject.layout.projectDirectory.asFile.apply {
        delete(resolve("iosApp/Pods"))
        delete(resolve("iosApp/iosApp.xcworkspace"))
        delete(resolve("iosApp/Podfile.lock"))
    }
}