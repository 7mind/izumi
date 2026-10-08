package izumi.functional.bio.test

trait BIOAsyncExpectedBehaviorTestPlatformSpecific[F[+_, +_]] extends BIOAsyncExpectedBehaviorJavaFutureTest[F] {
  this: BIOAsyncExpectedBehaviorTest[F] =>
  override protected def platformName: String = "JVM"
}
