package izumi.fundamentals.platform.build

/**
  * Makes a test scope class visible in ANOTHER module's test scope when IzumiInheritedTestScopesPlugin is used.
  *
  * @see https://izumi.7mind.io/sbt/#inherited-test-scopes for further details
  */
class ExposedTestScope extends scala.annotation.StaticAnnotation
