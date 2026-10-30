pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Auto-provisions JDK toolchains (via the Foojay Disco API) when Gradle needs a JVM it can't find.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

//includeBuild("../kmp-dataresult")
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // PREFER_SETTINGS rather than FAIL_ON_PROJECT_REPOS: the Kotlin plugin unconditionally
    // registers project-level repositories for the js/wasmJs toolchain downloads, which
    // FAIL_ON_PROJECT_REPOS rejects at registration time — declaring them here is not enough to
    // avoid that. PREFER_SETTINGS keeps the same guarantee that every dependency resolves from
    // this block; it just ignores project repositories instead of failing the build.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // Maven Central's snapshot repository (Central Portal). Releases come from mavenCentral()
        // above; this only serves -SNAPSHOT versions, so pointing a catalog entry at one is all it
        // takes to try a library's unreleased build.
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            name = "MavenCentralSnapshots"
            mavenContent { snapshotsOnly() }
        }

        // io.github.solcott:dataresult / :uistate / :dataresult-apollo, which used to be the
        // :dataresult and :uistate modules in this build, resolve from mavenCentral(). mavenLocal()
        // serves a SNAPSHOT published locally while iterating on the library.
        mavenLocal {
            content { includeGroup("io.github.solcott") }
        }

        // Toolchains the js/wasmJs targets download for themselves. Each is locked to the single
        // module it serves so it can never resolve anything else.
        ivy("https://nodejs.org/dist") {
            name = "Node.js Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen Distributions"
            patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "Countries"

include(":model")
include(":network")
include(":repository")
include(":presenter")
include(":ui")
include(":shared")
include(":shared-compose")
include(":app")
include(":web")
include(":desktop")
include(":apple")
