package izumi.fundamentals.platform.build

/**
  * Makes a test scope class visible in ANOTHER module's test scope when IzumiInheritedTestScopesPlugin is used.
  *
  * @see https://izumi.7mind.io/sbt/#inherited-test-scopes for further details
  */
@deprecated("This functionality has been removed. This annotation is a no-op and should be removed.")
class ExposedTestScope extends scala.annotation.StaticAnnotation
