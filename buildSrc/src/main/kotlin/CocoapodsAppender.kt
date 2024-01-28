import org.gradle.api.Task
import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.provider.Property
import java.io.File
import kotlin.reflect.KFunction1
import kotlin.reflect.full.declaredMemberProperties

class CocoapodsAppender private constructor() {
    class Builder {
        private final val file: File
        private final val isExpanded: Boolean

        internal constructor(file: File, isExpanded: Boolean) {
            if (!file.exists()) {
                throw IllegalArgumentException("file not exist")
            }
            this.file = file
            this.isExpanded = isExpanded
            lines = file.readText().lines().toMutableList()
        }

        constructor(file: File) : this(file, false)

        private var lines: MutableList<String>


        fun build() {
            if (isExpanded) {
                throw RuntimeException("can't build in expanded mode")
            }
            file.writeText(lines.joinToString("\n"))
        }

        fun expandedBuild() {
            if (isExpanded) {
                file.writeText(lines.joinToString("\n"))
            }
        }

        @SuppressWarnings
        fun append(index: Int, appendText: String): Builder {
            lines.add(index, appendText)
            return this
        }

        @SuppressWarnings
        fun appendFirst(appendText: String): Builder {
            lines.add(0, appendText)
            return this
        }

        @SuppressWarnings
        fun appendLast(appendText: String): Builder {
            lines.add(appendText)
            return this
        }

        @SuppressWarnings
        fun append(searchBy: String, appendText: String): Builder {
            val index = lines.indexOfFirst { it.contains(searchBy) }
            lines.add(index + 1, appendText)
            lines = lines.joinToString("\n").lines().toMutableList()
            return this
        }

        @SuppressWarnings
        fun appendInWholePodspecFile(appendText: String): Builder {
            // 默认加在设置iOS部署目标的后面,不影响其他设置
            return append("spec.ios.deployment_target", appendText)
        }

        @SuppressWarnings
        fun appendInWholeSyntheticPodfile(appendText: String): Builder {
            // 默认加在设置iOS部署目标的后面,不影响其他设置
            return append("target.build_configurations.each do |config|", appendText)
        }

        @SuppressWarnings
        fun replace(searchBy: String, replaceText: String): Builder {
            val index = lines.indexOfFirst { it.contains(searchBy) }
            if (index >= 0) {
                lines[index] = replaceText
            }
            lines = lines.joinToString("\n").lines().toMutableList()
            return this
        }

        @SuppressWarnings
        fun replace(index: Int, replaceText: String): Builder {
            if (index >= 0) {
                lines[index] = replaceText
            }
            lines = lines.joinToString("\n").lines().toMutableList()
            return this
        }

        /**
         * 检查xcode有没有安装Kotlin插件的,虽然不是必须的,但是我还是改成强制要求了,不能调试的代码没有意义
         * @return String 返回的字符串为植入到podspec中的代码,已处理好缩进
         *
         * english: Check if xcode has installed the Kotlin plugin. Although it is not necessary,
         * I still changed it to a mandatory requirement.
         * There is no point in code that cannot be debugged
         * @return String The returned string is the code implanted in podspec, and the indentation has been processed
         */
        fun xcodeKotlinCheck(spec: Any): Builder {
            if (spec::class.simpleName != "PodspecTask_Decorated") {
                throw IllegalArgumentException("xcodeKotlinCheck must be called in PodspecTask")
            }
            return appendInWholePodspecFile(
                """
            # 检查是否安装了 CFPropertyList gem
            unless Gem::Specification::find_all_by_name('CFPropertyList').any?
              puts "Installing CFPropertyList gem..."
              `gem install CFPropertyList`
            end
            require 'cfpropertylist'
            # 获取Xcode配置文件路径
            xcode_plist_path = File.expand_path("~/Library/Preferences/com.apple.dt.Xcode.plist")
            # 以ASCII-8BIT编码读取配置文件内容
            xcode_settings_binary = IO.binread(xcode_plist_path)
            # 解析 plist 文件
            xcode_settings = CFPropertyList.native_types(CFPropertyList::List.new(data: xcode_settings_binary).value)
            # 获取DerivedData目录
            derived_data_directory = xcode_settings["IDECustomDerivedDataLocation"] || xcode_settings["DerivedDataLocation"] || ""
            # 如果derived_data_directory 没有获得值,说明使用了默认的路径
            if derived_data_directory.empty?
              derived_data_directory = File.expand_path("~/Library/Developer/Xcode/DerivedData")
            end
            
            
            # 查找Kotlin插件
            plug = File.expand_path(File.join(File.dirname(derived_data_directory), "/Plug-ins"))
            
            # 检查是否存在插件目录
            if plug.empty?
              raise "plug directory not found. Please check your Xcode settings. plug directory as #{plug}, founded by query DerivedData directory parent"
            end
            
            kotlin_plugin_found = Dir.glob("#{plug}/*.ideplugin").any? { |path| path.include?('Kotlin') }
            
            # 打印结果
            puts "Kotlin插件 #{kotlin_plugin_found ? '已安装.' : '未安装.'}"
            unless kotlin_plugin_found
              raise "XCode Kotlin Plugin is not installed. Please install it using
              ```
              brew install xcode-kotlin & \\
              xcode-kotlin install
              ```."
            end
                """.trimIndent()
                    .prependIndent("    ")
            )
        }

        fun deploymentTarget(podGen: Any, target: String? = null): Builder {
            val deploymentTarget = if (target == null) {
                if (podGen::class.simpleName != "PodGenTask_Decorated") {
                    throw IllegalArgumentException("deploymentTarget must be called in PodGenTask")
                }
                val platformSettingsVar: DefaultProperty<*>? =
                    podGen::class.declaredMemberProperties.find { it.name == "platformSettings" }?.getter?.call(
                        podGen
                    ) as? DefaultProperty<*>
                val platformSettings = platformSettingsVar?.get()
                    ?: throw IllegalArgumentException("PodGenTask platformSettings not found")

                platformSettings::class.declaredMemberProperties.find { it.name == "deploymentTarget" }
                    ?.let { return@let it.getter.call(platformSettings) }
                    ?: throw IllegalArgumentException("PodGenTask deploymentTarget not found")
            } else {
                target
            }
            //   config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = ""
//            config.build_settings['ONLY_ACTIVE_ARCH'] = 'YES'

            val indent = "      "
            val content = """
                xcconfig_path = config.base_configuration_reference.real_path
                xcconfig = File.read(xcconfig_path)
                xcconfig_mod = xcconfig.gsub(/DT_TOOLCHAIN_DIR/, "TOOLCHAIN_DIR")
                File.open(xcconfig_path, "w") { |file| file << xcconfig_mod }
                """.trimIndent()
                .replaceIndent(indent)
            return appendInWholeSyntheticPodfile(content)
                .appendOrCreate(
                    "if config.base_configuration_reference",
                    """
                 config.build_settings.delete 'IPHONEOS_DEPLOYMENT_TARGET'
                 config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = "$deploymentTarget"
                """.trimIndent().prependIndent("  "),
                    indent = indent,
                    begin = "if config.base_configuration_reference",
                    end = "end",
                    func = ::appendInWholeSyntheticPodfile
                )
        }

        fun excludeArch(archs: List<String>, rollback: Boolean,isPodspecType:Boolean=false): Builder {
            val indent = "      "
            if(isPodspecType){
                if(isExist("'EXCLUDED_ARCHS[sdk=iphonesimulator*]' =>")){
                    remove("'EXCLUDED_ARCHS[sdk=iphonesimulator*]' =>")
                }
                if (rollback) {
                    return this
                }
                return appendOrCreate(
                    "spec.pod_target_xcconfig = {",
                    """
                    'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => '${archs.joinToString(" ")}',
                    """.trimIndent().prependIndent("   "),
                    indent = indent,
                    begin = "spec.pod_target_xcconfig = {",
                    end = "}",
                    func = ::appendInWholeSyntheticPodfile
                )
            }else{
                if (isExist("config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]']")) {
                    remove("config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]']")
                }
                if (rollback) {
                    return this
                }
                return appendOrCreate(
                    "if config.base_configuration_reference",
                    """
                 config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = "${
                        archs.joinToString(
                            " "
                        )
                    }"
                """.trimIndent().prependIndent("  "),
                    indent = indent,
                    begin = "if config.base_configuration_reference",
                    end = "end",
                    func = ::appendInWholeSyntheticPodfile
                )
            }
        }

        fun relinkGradle(projectDir: File, podSpecDir: File): Builder {
            val originDir =
                File("${projectDir.parentFile.absolutePath}/iosApp/Pods/../../${projectDir.name}/")
            val relativeTo = originDir.relativeTo(podSpecDir).path
            return replace(
                "REPO_ROOT=\"${"$"}PODS_TARGET_SRCROOT\"",
                "                REPO_ROOT=\"${"$"}PODS_TARGET_SRCROOT${if (relativeTo.isEmpty()) "" else "/$relativeTo"}\""
            )
        }

        fun sharedPodRelink(podSpecDir: File, rollback: Boolean = false): Builder? {
            var searchBy = "  pod 'shared', :path => '../shared'"
            val relative = "${podSpecDir.relativeTo(this.file.parentFile).path}/".replace("//", "/")
            var replaceBy = "  pod 'shared', :path => '${relative}'"
            if (rollback) {
                val old = searchBy
                searchBy = replaceBy
                replaceBy = old
            }
            val index = lines.indexOfFirst { it.contains(searchBy) }

            if (index >= 0) {
                return replace(searchBy, replaceBy)
            } else {
                val index2 = lines.indexOfFirst { it.contains("pod 'shared'") }
                if (index2 >= 0) {
                    return replace(index2, replaceBy)
                }
            }
            return null
        }

        /**
         * 中文: 我也不知道这个有没有意义,自定义pod的构建目录,因为查看xcode中在Pods目录的同级父目录中有一个build文件夹,但是实际上这个目录并没有创建
         * english: I don't know if this makes sense. Customize the build directory of the pod.
         * Because I see a build folder in the same level parent folder of the Pods folder in xcode, but in fact this folder is not created
         *
         */
        fun rewriteSymroot(buildDir: File, projectDir: File, rollback: Boolean = false): Builder {
            val shouldChangePodspecDir =
                !(buildDir.parentFile.absolutePath == projectDir.absolutePath)
            val isBuildDirChanged = shouldChangePodspecDir

            if (isBuildDirChanged) {
                var relativeFile =
                    buildDir.relativeTo(projectDir.parentFile.resolve("iosApp/Pods/"))
                if (relativeFile.parentFile.name == "build") {
                    relativeFile = relativeFile.parentFile
                }
                val relative = relativeFile.path
                if (!isExist("config.build_settings['PODS_BUILD_DIR']")) {
                    appendOrCreate(
                        "if config.base_configuration_reference",
                        """
                 config.build_settings['PODS_BUILD_DIR'] = "${'$'}(SRCROOT)/$relative/ios"
                 config.build_settings['SYMROOT'] = "${'$'}(SRCROOT)/$relative/ios"
                """.trimIndent().prependIndent("   "),
                        indent = "      ",
                        begin = "if config.base_configuration_reference",
                        end = "end",
                        func = ::appendInWholeSyntheticPodfile
                    )
                }
            } else {
                if (rollback) {
                    remove("config.build_settings['PODS_BUILD_DIR']")
                    remove("config.build_settings['SYMROOT']")
                }
            }
            return this
        }

        @SuppressWarnings
        fun remove(searchBy: String) {
            val index = lines.indexOfFirst {
                it.contains(searchBy)
            }
            if (index >= 0) {
                lines.removeAt(index)
                lines = lines.joinToString("\n").lines().toMutableList()
            }
        }

        @SuppressWarnings
        fun isExist(searchBy: String): Boolean {
            val index = lines.indexOfFirst {
                it.contains(searchBy)
            }
            return index >= 0
        }

        @SuppressWarnings
        fun appendOrCreate(
            searchBy: String,
            appendText: String,
            indent: String,
            begin: String,
            end: String,
            func: KFunction1<String, Builder>
        ): Builder {
            val index = lines.indexOfFirst {
                it.contains(searchBy)
            }
            if (index >= 0) {
                lines.add(index + 1, appendText.prependIndent(indent))
                lines = lines.joinToString("\n").lines().toMutableList()
            } else {
                val newLines = mutableListOf<String>()
                newLines.add(begin)
                newLines.add(appendText)
                newLines.add(end)
                func.invoke(
                    newLines.joinToString("\n")
                        .prependIndent(indent)
                )
            }
            return this
        }
    }

    class TaskBuilder(
        private val task: Task,
        private val buildDir: File,
        private val projectDir: File
    ) {
        private lateinit var podBuilder: CocoapodsAppender.Builder
        private lateinit var closure: (CocoapodsAppender.Builder) -> Unit
        private lateinit var buildExec: () -> Unit
        val isBuildDirChanged: Boolean
            get() {
                val shouldChangePodspecDir =
                    !(buildDir.parentFile.absolutePath == projectDir.absolutePath)
                return shouldChangePodspecDir
            }

        val podSpecDir: File
            get() {
                if (isBuildDirChanged) {
                    return calculateCommonDirectory(projectDir, buildDir)
                } else {
                    return projectDir
                }
            }

        fun relinkPodspec(): TaskBuilder {
            @Suppress("UNCHECKED_CAST")
            val outputDir: Property<File>? =
                task::class.declaredMemberProperties.find { it.name == "outputDir" }?.getter?.call(
                    task
                ) as? Property<File>
            if (isBuildDirChanged) {
                var ignoreDistributionDir = false
                var oldOutputDir: File? = null
                if (outputDir != null) {
                    val old = outputDir.get()
                    if (
                        !(old.absolutePath.endsWith("/cocoapods/publish/release")
                                &&
                                old.absolutePath.endsWith("/cocoapods/publish/debug"))
                    ) {
                        oldOutputDir = old
                        outputDir.set(podSpecDir)
                    } else {
                        ignoreDistributionDir = true
                    }
                }
                task.doLast {
                    if (!ignoreDistributionDir && oldOutputDir != null) {
                        // 查找old目录中的podspec文件
                        val podspecFile =
                            oldOutputDir.listFiles()?.find { it.name.endsWith(".podspec") }
                        podspecFile?.delete()
                    }
                    val outputFileCaller =
                        task::class.declaredMemberProperties.find { it.name == "outputFile" }!!
                    val outputFile = (outputFileCaller.getter.call(task) as File)
                    outputFile.apply {
                        podBuilder = CocoapodsAppender.Builder(this, true)
                        if (isBuildDirChanged) {
                            podBuilder.appendFirst("# podspec can't be link relative for parent directory, so we change podspec file to parent directory 😂 ")
                        }
                        if (::closure.isInitialized) {
                            closure(podBuilder)
                        }
                        if (::buildExec.isInitialized) {
                            buildExec()
                        }
                    }
                }
            } else {
                task.doLast {
                    if (outputDir != null) {
                        val old = outputDir.get().parentFile
                        // 查找old目录中的podspec文件
                        val podspecFile = old.listFiles()?.find { it.name.endsWith(".podspec") }
                        podspecFile?.delete()
                        val current =
                            old.resolve("${this@TaskBuilder.projectDir.name}/${this@TaskBuilder.projectDir.name}.podspec")
                        podBuilder = CocoapodsAppender.Builder(current, true)
                        if (::closure.isInitialized) {
                            closure(podBuilder)
                        }
                        if (::buildExec.isInitialized) {
                            buildExec()
                        }
                    }
                }
            }
            return this
        }

        fun withClosure(closure: (CocoapodsAppender.Builder) -> Unit): TaskBuilder {
            this.closure = closure
            return this
        }

        fun build() {
            this.buildExec = {
                podBuilder.expandedBuild()
            }
        }
    }
}

fun calculateCommonDirectory(projectDirFile: File, buildDirFile: File): File {
    val absoluteProjectDir = projectDirFile.absoluteFile
    val absoluteBuildDir = buildDirFile.absoluteFile
    val projectComponents =
        absoluteProjectDir.toPath().toAbsolutePath().iterator().asSequence().toList()
    val buildComponents =
        absoluteBuildDir.toPath().toAbsolutePath().iterator().asSequence().toList()
    val commonComponents =
        projectComponents.zip(buildComponents).takeWhile { (p, b) -> p == b }.map { it.first }
    var str = commonComponents.joinToString(File.separator)
    if (!str.startsWith(File.separator)) {
        str = "${File.separator}$str"
    }
    val commonParentDir = File(str)
    return commonParentDir
}