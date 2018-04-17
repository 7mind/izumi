package com.github.pshirshov.izumi.idealingua.translator.toscala

import scala.meta._

class CirceGenericTranslatorExtension extends CirceTranslatorExtensionBase {
  override protected val classDeriverImports: List[Import] = List(
    q""" import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder} """
  )
}
