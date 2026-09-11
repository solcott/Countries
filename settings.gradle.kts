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

        // io.github.solcott:dataresult / :uistate / :dataresult-apollo, which used to be the
        // :dataresult and :uistate modules in this build. mavenLocal() first so a locally
        // published SNAPSHOT wins while iterating on the library.
        mavenLocal {
            content { includeGroup("io.github.solcott") }
        }
        maven("https://maven.pkg.github.com/solcott/kmp-dataresult") {
            name = "GitHubPackages"
            // GitHub Packages authenticates even public reads, so this needs a classic PAT with
            // the read:packages scope in ~/.gradle/gradle.properties. See the library's README.
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
            // Nothing else may resolve here, so a credential problem can't cascade.
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
