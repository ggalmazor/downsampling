import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

buildscript {
  repositories { mavenCentral() }
  dependencies {
    classpath("org.commonmark:commonmark:0.24.0")
    classpath("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
  }
}

plugins {
  `java-library`
  checkstyle
  id("com.vanniktech.maven.publish") version "0.36.0"
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
    languageVersion = JavaLanguageVersion.of(21)
  }
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}

val generateJavadocOverview by tasks.registering {
  val readmeFile = rootProject.file("README.md")
  val overviewFile = layout.buildDirectory.file("javadoc-overview/overview.html")
  inputs.file(readmeFile)
  outputs.file(overviewFile)

  doLast {
    val markdown = readmeFile.readText()
    val extensions = listOf(TablesExtension.create())
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder().extensions(extensions).build()
    val html = renderer.render(parser.parse(markdown))
    val output = overviewFile.get().asFile
    output.parentFile.mkdirs()
    // Escape @ inside <pre> blocks so Javadoc doesn't mistake them for tag starts.
    val escaped = html.replace(Regex("(?s)(<pre[^>]*>)(.*?)(</pre>)")) { m ->
      m.groupValues[1] + m.groupValues[2].replace("@", "&#64;") + m.groupValues[3]
    }
    output.writeText("""
      <!DOCTYPE html>
      <html lang="en">
      <head><meta charset="UTF-8"><title>downsampling</title></head>
      <body>$escaped</body>
      </html>
    """.trimIndent())
  }
}

tasks.javadoc {
  dependsOn(generateJavadocOverview)
  source = sourceSets.main.get().allJava
  (options as StandardJavadocDocletOptions).apply {
    overview = generateJavadocOverview.get().outputs.files.singleFile.absolutePath
    addStringOption("Xdoclint:none", "-quiet")
  }
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()

  coordinates("com.ggalmazor", "downsampling", "21.2.0")

  pom {
    name.set("downsampling")
    description.set("Java library providing LTTB, RDP, and PIP time-series downsampling algorithms")
    inceptionYear.set("2025")
    url.set("https://github.com/ggalmazor/downsampling")
    licenses {
      license {
        name.set("Apache-2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        distribution.set("repo")
      }
    }
    developers {
      developer {
        id.set("ggalmazor")
        name.set("Guillermo Gutierrez Almazor")
        url.set("https://github.com/ggalmazor/")
      }
    }
    scm {
      url.set("https://github.com/ggalmazor/downsampling/")
      connection.set("scm:git:git://github.com/ggalmazor/downsampling.git")
      developerConnection.set("scm:git:ssh://git@github.com/ggalmazor/downsampling.git")
    }
  }
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
