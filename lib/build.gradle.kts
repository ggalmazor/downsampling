plugins {
  `java-library`
  checkstyle
  id("me.champeau.jmh") version "0.7.3"
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(libs.junit.jupiter)
  testImplementation("org.hamcrest:hamcrest:3.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  jmh("org.openjdk.jmh:jmh-core:1.37")
  jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}

tasks.javadoc {
  source = sourceSets.main.get().allJava
}

checkstyle {
  toolVersion = "10.21.4"
  configFile = file("../config/checkstyle/checkstyle.xml")
  configProperties["org.checkstyle.google.suppressionfilter.config"] =
    file("../config/checkstyle/checkstyle-suppressions.xml").absolutePath
}

tasks.named<Checkstyle>("checkstyleTest") { isEnabled = false }
tasks.named<Checkstyle>("checkstyleJmh") { isEnabled = false }

jmh {
  iterations = 5
  warmupIterations = 3
  fork = 2
  benchmarkMode = listOf("avgt")
  timeUnit = "ms"
}
