package izumi.fundamentals.platform.cache

trait CachedHashcode {
  protected def hash: Int
  override final lazy val hashCode: Int = hash
}
