// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    //hilt
    id("com.google.dagger.hilt.android") version "2.57.2" apply false

    id("com.google.devtools.ksp") version "2.3.2" apply false

    // AboutLibraries
    id("com.mikepenz.aboutlibraries.plugin") version "12.2.4" apply false
}

allprojects {
    tasks.register("resolveAllDependencies") {
        group = "dependency"
        description = "Resolves and downloads all dependencies for all configurations in all projects."

        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { config ->
                    println("🔍 Resolving ${config.name} in ${project.name}")
                    try {
                        config.resolve()
                    } catch (e: Exception) {
                        println("❌ Failed to resolve ${config.name} in ${project.name}: ${e.message}")
                    }
                }
        }
    }
}
