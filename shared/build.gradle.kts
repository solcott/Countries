plugins {
  id("kmp-library")
  alias(libs.plugins.metro)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":dataresult"))
      api(project(":model"))
      api(project(":repository"))
      // On the compile classpath so Metro aggregates NetworkProviders into CoreGraph:
      // contributions are resolved where @DependencyGraph is compiled, not where the graph is
      // used. `api` because contributed interfaces become supertypes of the generated graph, so
      // consumers of CoreGraph must see them as well.
      api(project(":network"))
      // `api`: Logger appears in provideLogger's signature and is injected by other modules.
      api(libs.kermit)
    }
  }
}
