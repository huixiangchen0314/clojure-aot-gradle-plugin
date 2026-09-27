package top.kzre.clojureaotgradleplugin

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*

open class ClojureAotExtension(project: Project) {
    val clojureVersion: Property<String> =
        project.objects.property(String::class.java).convention("1.12.0")
    val nreplVersion: Property<String> =
        project.objects.property(String::class.java).convention("1.0.0")
    val ciderVersion: Property<String> =
        project.objects.property(String::class.java).convention("0.28.5")
    val nreplPort: Property<Int> =
        project.objects.property(Int::class.java).convention(7888)
    val includeCider: Property<Boolean> =
        project.objects.property(Boolean::class.java).convention(true)
    val namespaces: ListProperty<String> =
        project.objects.listProperty(String::class.java).convention(emptyList())
    val clojureSourceDir: Property<String> =
        project.objects.property(String::class.java).convention("src/main/clojure")

    val pureJava: PureJavaConfig = PureJavaConfig(project)

    fun pureJava(action: PureJavaConfig.() -> Unit) {
        action(pureJava)
    }
}