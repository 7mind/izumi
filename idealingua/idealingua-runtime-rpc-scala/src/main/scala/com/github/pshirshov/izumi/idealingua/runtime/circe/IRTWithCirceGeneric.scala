package com.github.pshirshov.izumi.idealingua.runtime.circe

import io.circe.generic.decoding.DerivedDecoder
import io.circe.generic.encoding.DerivedObjectEncoder
import io.circe.{Decoder, Encoder}
import shapeless.{Cached, Lazy}

/**
 * this will give codecs that cache derivation resolution _within compiler session_ (_NOT between compiler runs_)
 * Can't define it so that it would cache between _compiler runs_
 * because of a shapeless bug https://github.com/milessabin/shapeless/issues/837 preventing a definition such as
 *  {{{
 *   abstract class WithCirceGeneric[A: DerivedObjectEncoder: DerivedDecoder] {
 *     implicit val enc: Encoder[A] = implicitly[DerivedObjectEncoder[A]]
 *     implicit val dec: Decoder[A] = implicitly[DerivedDecoder[A]]
 *   }
 *
 *   final case class X(int: Int)
 *
 *   object X extends WithCirceGeneric[X]
 *   }}}
 *
 * with error
 *
 * Error:(26, 19) super constructor cannot be passed a self reference unless parameter is declared by-name
 * object X extends WithCirceGeneric[X]
 *
 * (if you rename object to e.g. `XCodecs` it will work though)
 *
 * This is still useful to derive sealed traits since WithCirce doees not support them:
 *   {{{
 *   sealed trait Y
 *   final case class Ya() extends Y
 *
 *   object Ya extends WithCirce[Ya]
 *   object Y extends WithCirceGeneric[Y]
 *   }}}
 */
trait IRTWithCirceGeneric[A] {
  implicit def enc(implicit ev: Cached[Lazy[DerivedObjectEncoder[A]]]): Encoder[A] = ev.value.value
  implicit def dec(implicit ev: Cached[Lazy[DerivedDecoder[A]]]): Decoder[A] = ev.value.value
}
