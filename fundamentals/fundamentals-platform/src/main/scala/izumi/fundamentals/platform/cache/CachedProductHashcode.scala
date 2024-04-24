package izumi.fundamentals.platform.cache

trait CachedProductHashcode { this: Product =>
  override final lazy val hashCode: Int = {
    scala.runtime.ScalaRunTime._hashCode(this)
  }
}
