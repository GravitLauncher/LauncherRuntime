plugins {
    id("java-library")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    archiveFileName.set(project.name+".jar")
    manifest {
        attributes(
            "Module-Main-Class" to "pro.gravit.launcher.gui.JavaRuntimeModule",
            "Module-Config-Class" to "pro.gravit.launcher.gui.core.config.GuiModuleConfig",
            "Module-Config-Name" to "JavaRuntime",
            "Required-Modern-Java" to "true"
        )
    }
}

javafx {
    version = "27.0.1.1"
    modules("javafx.fxml", "javafx.controls", "javafx.web", "javafx.base")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://maven.gravitlauncher.com")
    }
}

dependencies {
    compileOnly("org.slf4j:slf4j-api:2.0.18")
    implementation("com.gravitlauncher.launcher:launcher-runtime:5.8.0-SNAPSHOT")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.18")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useJUnitPlatform()
}
val runtimeZip = tasks.register<Zip>("runtimeZip") {
    archiveFileName.set("runtime.zip")
    from("runtime")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
tasks.assemble { dependsOn(runtimeZip) }
