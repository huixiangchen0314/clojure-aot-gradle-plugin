package top.kzre.clojureaotgradleplugin

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*

open class PureJavaConfig(project: Project) {
    val includes: ListProperty<String> =
        project.objects.listProperty(String::class.java).convention(emptyList())
    val excludes: ListProperty<String> =
        project.objects.listProperty(String::class.java).convention(emptyList())
    /** "pure" | "impure" */
    val defaultMode: Property<String> =
        project.objects.property(String::class.java).convention("pure")
}