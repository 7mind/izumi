package izumi.fundamentals.platform.cache

trait CachedHashcode {
  protected def hash: Int
  override final lazy val hashCode: Int = hash
}

trait CachedProductHashcode { this: Product =>
  override final lazy val hashCode: Int = {
    scala.runtime.ScalaRunTime._hashCode(this)
  }
}
