rootProject.name = "JavaRuntime"

// Opt in to the matching source checkout instead of a published SNAPSHOT.
providers.gradleProperty("launcherSource").orNull?.let { source ->
    includeBuild(source) {
        dependencySubstitution {
            listOf("launcher-utils", "launcher-core", "launcher-api", "launcher-client", "launcher-start", "launcher-runtime").forEach { name ->
                substitute(module("com.gravitlauncher.launcher:$name")).using(project(":components:$name"))
            }
        }
    }
}
