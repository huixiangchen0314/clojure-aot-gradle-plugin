package top.kzre.clojureaotgradleplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import java.io.File

class ClojureAotPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply("java")

        val extension = project.extensions.create(
            "clojure",
            ClojureAotExtension::class.java,
            project
        )

        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExtension.sourceSets.getByName("main")

        val javaClassesDirProvider: Provider<Directory> =
            mainSourceSet.java.destinationDirectory
        val javaClassesDir: File = javaClassesDirProvider.get().asFile

        disableStandardJavaCompile(project)

        val clojureOutDir = project.layout.buildDirectory
            .dir("classes/clojure/main")
            .get().asFile

        project.afterEvaluate {
            configureDependencies(project, extension)

            registerJavaCompilationTasks(
                project,
                extension,
                javaClassesDirProvider,
                javaClassesDir,
                clojureOutDir
            )

            project.tasks.withType(Jar::class.java).configureEach {
                if (name == "jar" || name == "bootJar") {
                    from(clojureOutDir) { into("") }
                    dependsOn("compileImpureJava")
                }
            }

            registerNreplTask(project, extension)
        }
    }

    // 1. 依赖配置
    private fun configureDependencies(project: Project, ext: ClojureAotExtension) {
        project.dependencies.add(
            "implementation",
            "org.clojure:clojure:${ext.clojureVersion.get()}"
        )
        project.dependencies.add(
            "runtimeOnly",
            "nrepl:nrepl:${ext.nreplVersion.get()}"
        )
        if (ext.includeCider.get()) {
            project.dependencies.add(
                "runtimeOnly",
                "cider:cider-nrepl:${ext.ciderVersion.get()}"
            )
        }
    }

    // 2. 禁用默认 compileJava
    private fun disableStandardJavaCompile(project: Project) {
        project.tasks.named("compileJava", JavaCompile::class.java).configure {
            enabled = false
        }
    }

    // 3. 注册三段编译任务
    private fun registerJavaCompilationTasks(
        project: Project,
        ext: ClojureAotExtension,
        javaClassesDirProvider: Provider<Directory>,
        javaClassesDir: File,
        clojureOutDir: File
    ) {
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExtension.sourceSets.getByName("main")

        // ★ annotation processor path（Lombok 等）
        val annotationProcessorPath = mainSourceSet.annotationProcessorPath

        val (pureJavaTree, impureJavaTree) =
            scanAndSplitSources(project, mainSourceSet, ext.pureJava)

        val clojureSourceDir = project.file(ext.clojureSourceDir.get())

        println("[ClojureAot] pureJava files:   ${pureJavaTree.files.size}")
        println("[ClojureAot] impureJava files: ${impureJavaTree.files.size}")
        println("[ClojureAot] excludes:         ${ext.pureJava.excludes.getOrElse(emptyList())}")
        println("[ClojureAot] includes:         ${ext.pureJava.includes.getOrElse(emptyList())}")
        println("[ClojureAot] defaultMode:      ${ext.pureJava.defaultMode.getOrElse("pure")}")
        println("[ClojureAot] javaClassesDir:   ${javaClassesDir.absolutePath}")
        println("[ClojureAot] clojureOutDir:    ${clojureOutDir.absolutePath}")
        println("[ClojureAot] clojureSourceDir: ${clojureSourceDir.absolutePath}")
        println("[ClojureAot] annotationProcessorPath: ${annotationProcessorPath?.files?.size ?: 0} entries")

        // ── 第一段：纯 Java ──
        val compilePureJava = project.tasks.register<JavaCompile>("compilePureJava") {
            group = "build"
            description = "编译纯 Java（不依赖 Clojure 生成的类）"
            source = pureJavaTree
            classpath = mainSourceSet.compileClasspath
            destinationDirectory.set(javaClassesDirProvider)
            dependsOn("processResources")
            options.encoding = "UTF-8"

            // ★ 关键：让注解处理器生效（Lombok 等）
            if (annotationProcessorPath != null) {
                options.annotationProcessorPath = annotationProcessorPath
            }

            doFirst {
                val srcFiles = source.files
                val destDir = destinationDirectory.get().asFile
                destDir.mkdirs()
                println("[compilePureJava] compiling ${srcFiles.size} files -> $destDir")
                if (srcFiles.isEmpty()) {
                    println("[compilePureJava] ⚠ WARNING: source is EMPTY!")
                }
            }
        }

        // ── 第二段：Clojure AOT ──
        val compileClojure = project.tasks.register<JavaExec>("compileClojure") {
            group = "build"
            description = "AOT 编译 Clojure 源码"
            dependsOn(compilePureJava)

            systemProperty("clojure.compile.path", clojureOutDir.absolutePath)

            classpath = project.files(
                mainSourceSet.compileClasspath,
                javaClassesDir,
                clojureSourceDir
            )
            mainClass.set("clojure.main")

            val rawPatterns = ext.namespaces.get()
            val namespaces = if (rawPatterns.isEmpty()) {
                emptyList()
            } else {
                expandNamespacePatterns(project, clojureSourceDir, rawPatterns)
            }

            if (namespaces.isNotEmpty()) {
                args("-e", namespaces.joinToString(" ") { "(compile '$it)" })
            }

            doFirst {
                clojureOutDir.mkdirs()
                val classCount = javaClassesDir
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .count()
                println("[compileClojure] javaClassesDir has $classCount .class files")
                println("[compileClojure] namespaces count: ${namespaces.size}")
                if (classCount == 0) {
                    println("[compileClojure] ⚠ WARNING: javaClassesDir is EMPTY!")
                }
            }
            outputs.dir(clojureOutDir)
        }

        // ── 第三段：依赖 Clojure 的 Java ──
        val compileImpureJava = project.tasks.register<JavaCompile>("compileImpureJava") {
            group = "build"
            description = "编译依赖 Clojure 的 Java"
            source = impureJavaTree
            classpath = project.files(
                mainSourceSet.compileClasspath,
                javaClassesDir,
                clojureOutDir
            )
            destinationDirectory.set(javaClassesDirProvider)
            dependsOn(compileClojure)
            options.encoding = "UTF-8"

            // ★ 同样配置注解处理器
            if (annotationProcessorPath != null) {
                options.annotationProcessorPath = annotationProcessorPath
            }

            doFirst {
                val srcFiles = source.files
                println("[compileImpureJava] compiling ${srcFiles.size} files")
                if (srcFiles.isEmpty()) {
                    println("[compileImpureJava] ℹ source is empty (all Java is pure)")
                }
            }
        }

        project.tasks.named("classes").configure {
            dependsOn(compileImpureJava)
        }
    }

    // 4. nREPL
    private fun registerNreplTask(project: Project, ext: ClojureAotExtension) {
        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExtension.sourceSets.getByName("main")
        val clojureSourceDir = project.file(ext.clojureSourceDir.get())

        project.tasks.register<JavaExec>("nrepl") {
            group = "development"
            description = "启动 nREPL 服务器（端口 ${ext.nreplPort.get()}）"
            classpath = project.files(
                mainSourceSet.runtimeClasspath,
                clojureSourceDir
            )
            mainClass.set("clojure.main")
            args("-m", "nrepl.cmdline", "--port", ext.nreplPort.get().toString())
            if (ext.includeCider.get()) {
                args("--middleware", "[cider.nrepl/cider-middleware]")
            }
        }
    }

    // 5. 扫描并拆分 Java 源文件
    private fun scanAndSplitSources(
        project: Project,
        sourceSet: SourceSet,
        config: PureJavaConfig
    ): Pair<FileTree, FileTree> {
        val classifier = Classifier(config)
        val pureFiles = mutableListOf<File>()
        val impureFiles = mutableListOf<File>()

        val allJavaFiles: Set<File> = sourceSet.allJava.files
        val srcDirs: List<File> = sourceSet.java.srcDirs.toList()

        println("[scanAndSplitSources] srcDirs: $srcDirs")
        println("[scanAndSplitSources] total java files from Gradle: ${allJavaFiles.size}")

        allJavaFiles.forEach { file ->
            val qname = qualifiedName(file, srcDirs)
            val pure = classifier.isPure(qname)
            if (pure) pureFiles += file else impureFiles += file
            if (!pure) println("[scanAndSplitSources]   impure: $qname")
        }

        return project.files(pureFiles).asFileTree to
                project.files(impureFiles).asFileTree
    }

    private fun qualifiedName(file: File, srcDirs: List<File>): String {
        val filePath = file.absolutePath
        val root = srcDirs.firstOrNull { dir ->
            val prefix = dir.absolutePath + File.separator
            filePath.startsWith(prefix)
        }
        val qname = if (root != null) {
            file.relativeTo(root).path
                .removeSuffix(".java")
                .replace(File.separatorChar, '.')
                .replace('/', '.')
        } else {
            file.nameWithoutExtension
        }
        return qname.ifBlank { file.nameWithoutExtension }
    }

    // 6. 展开 namespace pattern
    private fun expandNamespacePatterns(
        project: Project,
        sourceDir: File,
        patterns: List<String>
    ): List<String> {
        if (!sourceDir.exists()) return emptyList()
        val allNamespaces = sourceDir.walkTopDown()
            .filter { it.isFile && (it.extension == "clj" || it.extension == "cljc") }
            .mapNotNull { fileToNamespace(it, sourceDir) }
            .toList()

        if (patterns.isEmpty()) return allNamespaces

        return allNamespaces.filter { ns ->
            patterns.any { pat ->
                matchSegments(pat.split('.'), ns.split('.'), 0, 0)
            }
        }
    }

    private fun fileToNamespace(file: File, sourceDir: File): String? {
        val rel = file.relativeTo(sourceDir).path
            .removeSuffix(".cljc")
            .removeSuffix(".clj")
            .replace(File.separatorChar, '.')
            .replace('/', '.')
            .replace('_', '-')       // ★ 关键：下划线 → 连字符
        return rel.ifBlank { null }
    }

    private fun matchSegments(
        pat: List<String>,
        name: List<String>,
        pIdx: Int,
        nIdx: Int
    ): Boolean {
        if (pIdx == pat.size && nIdx == name.size) return true
        if (pIdx == pat.size) return false
        if (nIdx == name.size) {
            return (pIdx until pat.size).all { pat[it] == "**" }
        }
        return when (val p = pat[pIdx]) {
            "**" -> matchSegments(pat, name, pIdx + 1, nIdx) ||
                    matchSegments(pat, name, pIdx, nIdx + 1)
            "*"  -> matchSegments(pat, name, pIdx + 1, nIdx + 1)
            else -> p == name[nIdx] && matchSegments(pat, name, pIdx + 1, nIdx + 1)
        }
    }
}