package izumi.fundamentals.json.circe

import io.circe.Codec

/**
  * Provides circe codecs for case classes and sealed traits
  *
  * {{{
  *   final case class Abc(a: String, b: String, c: String)
  *
  *   object Abc extends WithCirce[Abc]
  * }}}
  *
  * To derive codecs for a sealed trait with branches inside its
  * own companion object, use a proxy object - this works around
  * a scala limitation: https://github.com/milessabin/shapeless/issues/837
  *
  * {{{
  *   sealed trait Abc
  *
  *   private abcCodecs extends WithCirce[Abc]
  *
  *   object Abc extends WithCirce(abcCodecs) {
  *     final case class A()
  *     object A extends WithCirce[A]
  *
  *     final case class B()
  *     object B extends WithCirce[B]
  *     final case class C()
  *
  *     object C extends WithCirce[C]
  *   }
  * }}}
  */
abstract class WithCirce[A]()(implicit derivedCodec: DerivationDerivedCodec[A]) {
  // workaround for https://github.com/milessabin/shapeless/issues/837
  def this(proxy: WithCirce[A]) = this()(DerivationDerivedCodec(proxy.codec))

  implicit val codec: Codec.AsObject[A] = derivedCodec.value
}



