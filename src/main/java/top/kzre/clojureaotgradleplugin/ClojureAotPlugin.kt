package top.kzre.clojureaotgradleplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.register


class ClojureAotPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("java")

        val extension = project.extensions.create("clojure", ClojureAotExtension::class.java, project)
        val clojureOutDir = project.layout.buildDirectory.dir("classes/clojure/main").get().asFile

        project.afterEvaluate {
            project.dependencies.add("implementation", "org.clojure:clojure:${extension.clojureVersion.get()}")
            project.dependencies.add("runtimeOnly", "nrepl:nrepl:${extension.nreplVersion.get()}")
            if (extension.includeCider.get()) {
                project.dependencies.add("runtimeOnly", "cider:cider-nrepl:${extension.ciderVersion.get()}")
            }
        }

        val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExtension.sourceSets.getByName("main")
        val javaClassesDir = mainSourceSet.java.destinationDirectory

        // 使用泛型注册 AOT 编译任务
        val compileClojure = project.tasks.register<JavaExec>("compileClojure") {
            group = "build"
            description = "AOT 编译 Clojure 源码"
            dependsOn("compileJava")

            systemProperty("clojure.compile.path", clojureOutDir.absolutePath)

            classpath = project.files(
                mainSourceSet.compileClasspath,
                javaClassesDir,
                project.file("src/main/clojure")
            )

            mainClass.set("clojure.main")

            val namespaces = extension.namespaces.get()
            if (namespaces.isNotEmpty()) {
                args("-e", namespaces.joinToString(" ") { "(compile '$it)" })
            }

            doFirst {
                clojureOutDir.mkdirs()
            }
            outputs.dir(clojureOutDir)
        }

        // 将 Clojure 产物打包
        project.tasks.withType(Jar::class.java).configureEach {
            if (name == "jar" || name == "bootJar") {
                from(clojureOutDir) { into("") }
                dependsOn(compileClojure)
            }
        }

        // 使用泛型注册 nREPL 任务
        project.tasks.register<JavaExec>("nrepl") {
            group = "development"
            description = "启动 nREPL 服务器（端口 ${extension.nreplPort.get()}）"

            classpath = project.files(
                mainSourceSet.runtimeClasspath,
                project.file("src/main/clojure")
            )

            mainClass.set("clojure.main")
            args("-m", "nrepl.cmdline", "--port", extension.nreplPort.get().toString())

            if (extension.includeCider.get()) {
                args("--middleware", "[cider.nrepl/cider-middleware]")
            }
        }
    }
}

open class ClojureAotExtension(project: Project) {
    val clojureVersion: Property<String> = project.objects.property(String::class.java).convention("1.12.0")
    val nreplVersion: Property<String> = project.objects.property(String::class.java).convention("1.0.0")
    val ciderVersion: Property<String> = project.objects.property(String::class.java).convention("0.28.5")
    val nreplPort: Property<Int> = project.objects.property(Int::class.java).convention(7888)
    val includeCider: Property<Boolean> = project.objects.property(Boolean::class.java).convention(true)
    val namespaces: ListProperty<String> = project.objects.listProperty(String::class.java).convention(listOf())
}