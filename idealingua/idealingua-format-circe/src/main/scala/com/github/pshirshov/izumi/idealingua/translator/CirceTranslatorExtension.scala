package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.translator.toscala.{ScalaTranslationContext, ScalaTranslatorExtension}

import scala.meta.Defn
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaMetaTools._

import scala.meta._

class CirceTranslatorExtension extends ScalaTranslatorExtension {
  override def handleCompositeCompanion(context: ScalaTranslationContext, id: TypeId, defn: Defn.Object): Defn.Object = {
    withDerivedEncoders(context, id, defn)
  }

  private def withDerivedEncoders[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T) = {
    val tpe = context.conv.toScala(id).typeFull
    val circeBoilerplate = List(
      q""" import _root_.io.circe.{Encoder, Decoder} """
      , q""" import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder} """
      ,
      q""" implicit val ${s"encode${id.name}"}: Encoder[$tpe] = deriveEncoder[$tpe] """
      ,
      q""" implicit val ${s"decode${id.name}"}: Decoder[$tpe] = deriveDecoder[$tpe] """
    )
    defn.extendDefinition(circeBoilerplate)
  }
}
