package izumi.distage

package object constructors {
  /**
    * @deprecated Since version `1.2.0`, default behavior for `make[T]` changed to not implicitly generate constructors
    *             for traits and abstract classes, or for "factory-like" traits and abstract classes, but only for concrete classes.
    *
    *             `AnyConstructor` as a proxy for default behavior of `make` has been removed in favor of [[ClassConstructor]],
    *             since it is the default behavior of `make` now. It is now recommended to use [[ClassConstructor]],
    *             [[TraitConstructor]] ([[https://izumi.7mind.io/distage/basics.html#auto-traits Auto-Traits]])
    *             and [[FactoryConstructor]] ([[https://izumi.7mind.io/distage/basics#auto-factories Auto-Factories]]) explicitly
    *             instead of using [[AnyConstructor]].
    */
  @deprecated("Removed since 1.2.0. Use ClassConstructor instead.")
  type AnyConstructor[T] = ClassConstructor[T]
  @deprecated("Removed since 1.2.0. Use ClassConstructor instead.")
  lazy val AnyConstructor: ClassConstructor.type = ClassConstructor
}
