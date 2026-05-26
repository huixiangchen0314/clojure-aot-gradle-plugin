
# clojure-aot-gradle-plugin

A Gradle plugin that adds Clojure AOT compilation and nREPL support to any
Java project (Spring Boot, plain Java, etc.) with minimal configuration.

## Features

- Automatically adds Clojure, nREPL, and optional CIDER middleware dependencies.
- Provides `compileClojure` task: compiles Java first, then AOT compiles the
  configured Clojure namespaces. The output is placed in
  `build/classes/clojure/main`.
- Clojure compiled classes are automatically included in `jar` or `bootJar`
  (if the Spring Boot plugin is also applied).
- Provides `nrepl` task: starts an nREPL server (default port 7888) with
  optional CIDER middleware, ready for interactive development.

## Quick Start

1. Add the plugin to your project.

   In `settings.gradle.kts` add `mavenLocal()` if you installed the plugin
   locally:

   ```kotlin
   pluginManagement {
       repositories {
           mavenLocal()
           gradlePluginPortal()
           mavenCentral()
       }
   }
   ```

In `build.gradle.kts`:

   ```kotlin
   plugins {
       id("top.kzre.clojure-aot") version "1.0.0"
   }
   ```

2. Configure the Clojure namespaces you want to AOT-compile:

   ```kotlin
   clojure {
       namespaces.set(listOf(
           "top.kzre.negame.player.player",
           "top.kzre.negame.inventory.core"
       ))
   }
   ```

3. Build and develop:

    - Full build (includes Clojure classes):
      ```
      ./gradlew build
      ```
    - Compile only Clojure:
      ```
      ./gradlew compileClojure
      ```
    - Start nREPL server (default port 7888):
      ```
      ./gradlew nrepl
      ```
      Then connect with Emacs/CIDER (`cider-connect`), IntelliJ/Cursive, or
      any nREPL client.

## Configuration

All properties have sensible defaults. You can override them in the `clojure`
block:

| Property        | Type          | Default      | Description                           |
|-----------------|---------------|--------------|---------------------------------------|
| clojureVersion  | String        | "1.12.0"     | Clojure version                       |
| nreplVersion    | String        | "1.0.0"      | nREPL server version                  |
| ciderVersion    | String        | "0.28.5"     | CIDER nREPL middleware version        |
| nreplPort       | Int           | 7888         | nREPL listening port                  |
| includeCider    | Boolean       | true         | Whether to include CIDER middleware   |
| namespaces      | List<String>  | []           | Namespaces to AOT compile (required)  |

Example:

```kotlin
clojure {
    clojureVersion.set("1.12.0")
    nreplPort.set(9090)
    includeCider.set(false)
    namespaces.set(listOf("myapp.core", "myapp.services"))
}
```

## How It Works

- The plugin applies the `java` plugin automatically.
- It creates a `compileClojure` task that depends on `compileJava`, so your
  Java interfaces/POJOs are available when Clojure compiles.
- The compiled Clojure classes are not added to `sourceSets.main.output`,
  avoiding circular dependencies. Instead they are directly included in
  `jar` (and `bootJar`) tasks.
- The `nrepl` task uses the full runtime classpath plus the Clojure source
  directory (`src/main/clojure`), enabling REPL-driven development with hot
  reloading of `.clj` files.

## Requirements

- Gradle 8.x+
- JDK 17 or higher
- The project must have the `java` plugin applied (the plugin adds it if
  missing).

## Publishing the Plugin

- To install locally:
  ```
  ./gradlew publishToMavenLocal
  ```
- To publish to a remote repository, configure the `maven-publish` plugin's
  `publishing` block in the plugin's own `build.gradle.kts`.

## License

MIT License
