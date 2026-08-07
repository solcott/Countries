plugins {
  id("kmp-library")
  alias(libs.plugins.metro)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(project(":shared"))
      api(project(":presenter"))
      // On the compile classpath so Metro aggregates :ui's CircuitProviders and Ui.Factory
      // multibindings into ComposeGraph. See the comment on ComposeGraph.
      api(project(":ui"))
      api(libs.circuit.foundation)

      // Every module contributing to AppScope has to be on the compile classpath of the module
      // that declares the graph, because Metro resolves contributions there. It also has to be
      // `api` rather than `implementation`: contributed interfaces become *supertypes* of the
      // generated graph, so consumers of ComposeGraph need to see them too.
      api(project(":network"))
    }
  }
}
