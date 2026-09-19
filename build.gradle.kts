plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.21"
}

group = "com.jelly.farmhelperv3"
version = providers.gradleProperty("version").get()
base { archivesName.set("FarmHelperV3") }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/")
    exclusiveContent {
        forRepository { ivy {
            name = "BaritoneReleases"
            url = uri("https://github.com/cabaletta/baritone/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
        } }
        filter { includeGroup("baritone") }
    }
}

val bundled by configurations.creating
configurations.implementation { extendsFrom(bundled) }

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.155.3+26.1.2")
    compileOnly("com.terraformersmc:modmenu:18.0.1")
    if (!providers.gradleProperty("withoutModMenu").isPresent) runtimeOnly("com.terraformersmc:modmenu:18.0.1")
    implementation("baritone:baritone-api-fabric:1.18.0")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    implementation("io.netty:netty-handler-proxy:4.2.7.Final")
    include("io.netty:netty-handler-proxy:4.2.7.Final")
    include("io.netty:netty-codec-socks:4.2.7.Final")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    include("org.java-websocket:Java-WebSocket:1.5.7")
    bundled("net.dv8tion:JDA:5.0.0-beta.24") {
        exclude(group = "org.slf4j")
        exclude(group = "net.java.dev.jna") // Minecraft supplies its current JNA runtime.
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

tasks.jar { from("LICENSE") { rename { "LICENSE_FarmHelperV3" } } }

// Fabric nests these libraries; the release jar does not require the old Forge JDA wrapper.
bundled.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
    val id = artifact.moduleVersion.id
    dependencies.add("include", "${id.group}:${id.name}:${id.version}" + (artifact.classifier?.let { ":$it" } ?: ""))
}

if (providers.gradleProperty("smokeTest").isPresent) {
    loom.mods {
        register("farmhelperv3") { sourceSet(sourceSets.main.get()) }
        register("farmhelperv3_checks") { sourceSet(sourceSets.test.get()) }
    }
    tasks.named("runClient") { dependsOn(tasks.testClasses) }
    loom.runs.named("client") {
        source(sourceSets.test.get())
        vmArg("-Dfarmhelperv3.smokeTest=true")
        if (providers.gradleProperty("settingsPreview").isPresent) vmArg("-Dfarmhelperv3.settingsPreview=true")
        if (providers.gradleProperty("settingsPreview").orNull == "confirmation") vmArg("-Dfarmhelperv3.confirmationPreview=true")
        if (providers.gradleProperty("checkWorld").isPresent) vmArg("-Dfarmhelperv3.checkWorld=true")
        runDir("build/smoke-run")
    }
}

val portChecks by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check event dispatch and V2 settings conversion without launching Minecraft."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.jelly.farmhelperv3.PortChecks")
}
tasks.check { dependsOn(portChecks) }

// Regression checks use a plain Java entrypoint instead of a JUnit dependency.
tasks.test { failOnNoDiscoveredTests.set(false) }
