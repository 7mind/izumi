//package com.github.pshirshov.izumi.idealingua.translator
//
//import io.circe.generic.decoding.DerivedDecoder
//import io.circe.generic.encoding.DerivedObjectEncoder
//import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
//import io.circe.{Decoder, Encoder}
//
//// TODO: move it into a runtime artifact
//
//trait WithCirce[A] {
//  implicit def enc(implicit ev: DerivedObjectEncoder[A]): Encoder[A] = deriveEncoder[A]
//  implicit def dec(implicit ev: DerivedDecoder[A]): Decoder[A] = deriveDecoder[A]
//}
