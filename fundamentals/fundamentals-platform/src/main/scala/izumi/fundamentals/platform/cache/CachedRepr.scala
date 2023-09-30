package izumi.fundamentals.platform.cache

trait CachedRepr {
  protected def repr: String

  override final lazy val toString: String = repr
}
