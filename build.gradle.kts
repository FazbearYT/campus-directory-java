plugins {
    application
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "ru.fazbear"
version = "1.0.0-lab1"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.12"
    modules = listOf("javafx.controls")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass = "ru.fazbear.campusdirectory.ContactDirectoryApp"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}
