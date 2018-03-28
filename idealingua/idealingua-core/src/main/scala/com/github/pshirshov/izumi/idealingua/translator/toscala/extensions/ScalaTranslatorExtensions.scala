package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslationContext

import scala.meta.Defn

class ScalaTranslatorExtensions(extensions: Seq[ScalaTranslatorExtension], context: ScalaTranslationContext) {
  def extend(modules: Seq[Module]): Seq[Module] = {
    extensions.foldLeft(modules) {
      case (acc, ext) =>
        ext.handleModules(context, acc)
    }
  }

  def extend[I, D <: Defn](id: I
                                     , entity: D
                                     , entityTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, D) => D
                                            ): D = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(context, id, acc)
    }
  }

  def extend[I, D <: Defn, C <: Defn]
  (
    id: I
    , entity: D
    , companion: C
    , entityTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, D) => D
    , companionTransformer: ScalaTranslatorExtension => (ScalaTranslationContext, I, C) => C
  ): Seq[Defn] = {
    val extendedEntity = extend(id, entity, entityTransformer)
    val extendedCompanion = extend(id, companion, companionTransformer)
    Seq(
      extendedEntity
      , extendedCompanion
    )
  }

}
