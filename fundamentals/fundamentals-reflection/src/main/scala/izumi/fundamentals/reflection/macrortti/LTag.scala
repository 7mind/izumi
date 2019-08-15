package izumi.fundamentals.reflection.macrortti

import scala.language.experimental.macros

/**
 * these are different summoners for light tags, it's fine for them to be the same structurally
 *
 * We don't have a "strong" tag at this point because TagMacro plays their role
 */
object LTag {
  final case class WeakHK[T](fullLightTypeTag: LightTypeTag)

  object WeakHK {
    implicit def materialize[T]: WeakHK[T] = macro LightTypeTagMacro.makeWeakHKTag[T]
  }

  final case class Weak[T](fullLightTypeTag: LightTypeTag)

  object Weak {
    def apply[T: Weak]: Weak[T] = implicitly

    implicit def materialize[T]: Weak[T] = macro LightTypeTagMacro.makeWeakTag[T]
  }
}
