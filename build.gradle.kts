plugins {
    id("java-library")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    archiveFileName.set(project.name+".jar")
    manifest {
        attributes(
            "Module-Main-Class" to "pro.gravit.launcher.gui.JavaRuntimeModule",
            "Module-Config-Class" to "pro.gravit.launcher.gui.core.config.GuiModuleConfig",
            "Module-Config-Name" to "JavaRuntime"
        )
    }
}

javafx {
    version = "22"
    modules("javafx.fxml", "javafx.controls", "javafx.web")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://maven.gravitlauncher.com")
    }
}

dependencies {
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    implementation("com.gravitlauncher.launcher:launcher-runtime:5.7.10")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}