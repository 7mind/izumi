package com.github.pshirshov.izumi.idealingua.translator.toscala

import scala.meta._

class CirceDerivationTranslatorExtension extends CirceTranslatorExtensionBase {
  override protected val classDeriverImports: List[Import] = List(
    q""" import _root_.io.circe.derivation.{deriveDecoder, deriveEncoder} """
  )

  override protected val adtDeriverImports: List[Import] = List(
    q""" import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder} """
  )
}
