plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "top.kzre"
version = "1.0.0"

gradlePlugin {
    plugins {
        register("clojureAot") {
            id = "top.kzre.clojure-aot"
            implementationClass = "top.kzre.clojureaotgradleplugin.ClojureAotPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 插件不需要额外依赖，kotlin-dsl 已经包含必要的 Gradle API
}