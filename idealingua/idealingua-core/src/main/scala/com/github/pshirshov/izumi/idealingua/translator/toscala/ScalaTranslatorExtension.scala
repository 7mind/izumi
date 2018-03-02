package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

import scala.meta.Defn

trait ScalaTranslatorExtension {
  def handleComposite(context: ScalaTranslationContext, id: TypeId, defn: Defn.Class): Defn.Class = defn
  def handleCompositeCompanion(context: ScalaTranslationContext, id: TypeId, defn: Defn.Object): Defn.Object = defn
}


