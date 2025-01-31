import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka")
    id("org.junit.platform.gradle.plugin")
    jacoco
    id("com.github.gmazzo.buildconfig") version "3.0.3"
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output
        compileClasspath += sourceSets["test"].output
        compileClasspath += configurations.testCompileClasspath

        runtimeClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["test"].output
        runtimeClasspath += configurations.testRuntimeClasspath
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/integrationTest/kotlin")
        }
    }
}

buildConfig {
    buildConfigField("String", "NAME", "\"${project.name}\"")
    buildConfigField("String", "VERSION", provider<String> { "\"${Versions.marathon}\"" })
    buildConfigField("String", "BUGSNAG_TOKEN", provider {
        val token = System.getenv("BUGSNAG_TOKEN") ?: ""
        "\"$token\""
    })
    buildConfigField("String", "RELEASE_MODE", provider {
        val releaseMode = Deployment.releaseMode ?: ""
        "\"$releaseMode\""
    })
}

dependencies {
    api(project(":configuration"))
    implementation(project(":report:html-report"))
    implementation(project(":report:execution-timeline"))

    implementation(Libraries.allure)
    implementation(Libraries.allureKotlinCommons)
    implementation(Libraries.allureEnvironment)

    implementation(project(":analytics:usage"))
    implementation(Libraries.gson)
    implementation(Libraries.jacksonAnnotations)
    implementation(Libraries.apacheCommonsIO)
    implementation(Libraries.kotlinStdLib)
    implementation(Libraries.kotlinCoroutines)
    implementation(Libraries.kotlinLogging)
    implementation(Libraries.logbackClassic)
    implementation(Libraries.influxDbClient)
    api(Libraries.koin)
    api(Libraries.bugsnag)
    testImplementation(project(":vendor:vendor-test"))
    testImplementation(TestLibraries.junit5)
    testImplementation(TestLibraries.kluent)
    testImplementation(TestLibraries.testContainers)
    testImplementation(TestLibraries.testContainersInflux)
    testImplementation(TestLibraries.mockitoKotlin)
    testImplementation(TestLibraries.koin)
    testImplementation(TestLibraries.xmlUnit)
    testRuntimeOnly(TestLibraries.jupiterEngine)
}

tasks.named<JacocoReport>("jacocoTestReport").configure {
    reports.xml.isEnabled = true
    reports.html.isEnabled = true
    dependsOn(tasks.named("test"))
}

val integrationTest = task<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    exclude("**/resources/**")

    shouldRunAfter("test")
}

tasks.withType<Test>().all {
    tasks.getByName("check").dependsOn(this)
    useJUnitPlatform()
}

junitPlatform {
    enableStandardTestTask = true
}

tasks.getByName("junitPlatformTest").outputs.upToDateWhen { false }
tasks.getByName("test").outputs.upToDateWhen { false }

Deployment.initialize(project)

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.apiVersion = "1.5"
}
