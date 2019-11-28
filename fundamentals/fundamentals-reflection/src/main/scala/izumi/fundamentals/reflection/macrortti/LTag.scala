package izumi.fundamentals.reflection.macrortti

import scala.language.experimental.macros

final case class LTag[T](tag: LightTypeTag)

/**
  * these are different summoners for light tags, it's fine for them to be the same structurally
  */
object LTag {
  def apply[T: LTag]: LTag[T] = implicitly

  implicit def materialize[T]: LTag[T] = macro LightTypeTagMacro.makeStrongTag[T]

  final case class StrongHK[T](tag: LightTypeTag)
  object StrongHK {
    implicit def materialize[T]: LTag.StrongHK[T] = macro LightTypeTagMacro.makeStrongHKTag[T]
  }

  final case class WeakHK[T](tag: LightTypeTag)
  object WeakHK {
    implicit def materialize[T]: LTag.WeakHK[T] = macro LightTypeTagMacro.makeWeakHKTag[T]
  }

  final case class Weak[T](tag: LightTypeTag)
  object Weak {
    def apply[T: LTag.Weak]: LTag.Weak[T] = implicitly

    implicit def materialize[T]: LTag.Weak[T] = macro LightTypeTagMacro.makeWeakTag[T]
  }
}
