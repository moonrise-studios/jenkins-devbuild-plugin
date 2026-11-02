import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
    id("org.jenkins-ci.jpi") version "0.57.0"
    id("com.github.spotbugs") version "6.5.13"
}

group = "gg.moonrise.jenkins"
description = "Posts build results to a Discord webhook as an embed and uploads the built jar files as Discord attachments."

val jenkinsBaselineVersion = "2.555.3"
val jenkinsBomVersion = "7280.ve2096dda_41b_9"
val jenkinsTestHarnessVersion = "2587.v9791b_4544b_b_2"
val junitVersion = "6.1.3"
val spotbugsToolVersion = "4.10.5"

jenkinsPlugin {
    jenkinsVersion.set(jenkinsBaselineVersion)
    shortName = "devbuild"
    displayName = "DevBuild Discord Publisher"
    url = "https://github.com/moonrise-studios/jenkins-devbuild-plugin"
    gitHub.set(uri("https://github.com/moonrise-studios/jenkins-devbuild-plugin"))
    extension.set("hpi")
    // Runs PluginAutomaticTestBuilder: Jelly syntax, escape-by-default and plugin load checks.
    generateTests.set(true)
    requireEscapeByDefaultInJelly.set(true)

    licenses {
        license {
            name.set("MIT License")
            url.set("https://opensource.org/license/mit/")
            distribution.set("repo")
        }
    }
    developers {
        developer {
            id.set("moonrise-studios")
            name.set("Moonrise Studios")
            url.set("https://github.com/moonrise-studios")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "jenkinsPublic"
        url = uri("https://repo.jenkins-ci.org/public/")
    }
}

dependencies {
    implementation(platform("io.jenkins.tools.bom:bom-2.555.x:$jenkinsBomVersion"))
    implementation("org.jenkins-ci.plugins:cloudbees-folder")
    implementation("org.jenkins-ci.plugins:credentials")
    implementation("org.jenkins-ci.plugins:plain-credentials")
    implementation("org.jenkins-ci.plugins:structs")

    testImplementation(platform("io.jenkins.tools.bom:bom-2.555.x:$jenkinsBomVersion"))
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.jenkins-ci.main:jenkins-test-harness:$jenkinsTestHarnessVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-job")
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-cps")
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-basic-steps")
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-durable-task-step")
    testImplementation("org.jenkins-ci.plugins.workflow:workflow-multibranch")
    testImplementation("org.jenkinsci.plugins:pipeline-model-definition")
    testImplementation("org.jenkins-ci.plugins:scm-api")
    testImplementation(group = "org.jenkins-ci.plugins", name = "scm-api", classifier = "tests")
    testImplementation("io.jenkins:configuration-as-code")
    testImplementation("io.jenkins.configuration-as-code:test-harness")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial,-classfile"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "1g"
    systemProperty("jenkins.test.timeout", "300")
}

spotbugs {
    toolVersion.set(spotbugsToolVersion)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.LOW)
    // Same noisy detectors the Jenkins plugin parent POM omits.
    omitVisitors.set(
        listOf(
            "ConstructorThrow",
            "FindReturnRef",
            "MultipleInstantiationsOfSingletons",
            "SharedVariableAtomicityDetector",
            "ThrowingExceptions",
            "FindImproperSynchronization",
        ),
    )
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(false) }
}

// Only production code is analysed, matching the Maven plugin parent POM.
tasks.named("spotbugsTest") { enabled = false }
tasks.named("spotbugsGeneratedJenkinsTest") { enabled = false }

tasks.named<org.jenkinsci.gradle.plugins.accmod.CheckAccessModifierTask>("checkAccessModifier") {
    ignoreFailures.set(false)
}
