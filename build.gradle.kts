import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  application

  kotlin("jvm") version "1.8.10"
  kotlin("plugin.serialization") version "1.8.10"

  id("com.github.johnrengelman.shadow") version "8.1.1"
  id("org.graalvm.buildtools.native") version "0.9.20"
}

repositories {
  mavenCentral()
}

java {
  val javaVersion = JavaVersion.toVersion(17)
  sourceCompatibility = javaVersion
  targetCompatibility = javaVersion
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-bom")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("io.ktor:ktor-client-cio:2.2.4")
  implementation("org.slf4j:slf4j-simple:2.0.7")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "17"
}

tasks.withType<Wrapper> {
  gradleVersion = "8.0.2"
}

application {
  mainClass.set("gay.pizza.yeencopy.MainKt")
}

graalvmNative {
  binaries {
    named("main") {
      imageName.set("yeencopy")
      mainClass.set("gay.pizza.yeencopy.MainKt")
      sharedLibrary.set(false)
    }
  }
}
