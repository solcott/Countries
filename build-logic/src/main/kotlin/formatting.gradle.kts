plugins { id("com.ncorti.ktfmt.gradle") }

ktfmt {
  googleStyle()
  removeUnusedImports = true
}
