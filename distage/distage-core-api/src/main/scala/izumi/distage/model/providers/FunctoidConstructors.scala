package izumi.distage.model.providers

import izumi.distage.constructors.{ClassConstructor, FactoryConstructor, TraitConstructor, ZEnvConstructor}
import zio.ZEnvironment

private[providers] trait FunctoidConstructors {
  /** Derive constructor for a concrete class `A` using [[ClassConstructor]] */
  def makeClass[A: ClassConstructor]: Functoid[A] = ClassConstructor[A]

  /**
    * Derive constructor for an abstract class or a trait `A` using [[TraitConstructor]]
    *
    * @see [[https://izumi.7mind.io/distage/basics.html#auto-traits Auto-Traits feature]]
    */
  def makeTrait[A: TraitConstructor]: Functoid[A] = TraitConstructor[A]

  /**
    * Derive constructor for a "factory-like" abstract class or a trait `A` using [[FactoryConstructor]]
    *
    * @see [[https://izumi.7mind.io/distage/basics.html#auto-factories Auto-Factories feature]]
    */
  def makeFactory[A: FactoryConstructor]: Functoid[A] = FactoryConstructor[A]

  /**
    * Derive constructor for a `zio.ZEnvironment` value `A` using [[ZEnvConstructor]]
    *
    * @see [[https://izumi.7mind.io/distage/basics.html#zio-environment-bindings ZIO Environment bindings]]
    */
  def makeHas[A: ZEnvConstructor]: Functoid[ZEnvironment[A]] = ZEnvConstructor[A]

  @deprecated("Same as `makeClass`, use `makeClass`")
  /** @deprecated Same as `makeClass`, use `makeClass` */
  def makeAny[A: ClassConstructor]: Functoid[A] = ClassConstructor[A]

}
