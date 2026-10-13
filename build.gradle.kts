plugins {
    id("com.android.application") version "8.7.2" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
}

val localBuildRoot = file("${System.getProperty("java.io.tmpdir")}\\screencontroller-build")

allprojects {
    // Stabilise les builds locaux sur dossiers synchronisés (ex: OneDrive)
    // en écrivant les artefacts Gradle hors du workspace.
    layout.buildDirectory.set(localBuildRoot.resolve(project.name))
}
