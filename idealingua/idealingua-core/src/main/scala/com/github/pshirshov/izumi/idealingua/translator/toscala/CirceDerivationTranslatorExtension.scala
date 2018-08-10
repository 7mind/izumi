package com.github.pshirshov.izumi.idealingua.translator.toscala

import scala.meta._

object CirceDerivationTranslatorExtension extends CirceTranslatorExtensionBase {
  override protected val classDeriverImports: List[Import] = List(
    q""" import _root_.io.circe.derivation.{deriveDecoder, deriveEncoder} """
  )
}
