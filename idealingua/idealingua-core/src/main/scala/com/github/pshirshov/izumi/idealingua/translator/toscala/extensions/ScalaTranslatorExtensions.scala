package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext

import scala.meta.Defn

class ScalaTranslatorExtensions(ctx: STContext, extensions: Seq[ScalaTranslatorExtension]) {
  def extend(modules: Seq[Module]): Seq[Module] = {
    extensions.foldLeft(modules) {
      case (acc, ext) =>
        ext.handleModules(ctx, acc)
    }
  }

  def extendX[S, P](source: S
                           , entity: P
                           , entityTransformer: ScalaTranslatorExtension => (STContext, S, P) => P
                          ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }

  def extend[I, D <: Defn](id: I
                                     , entity: D
                                     , entityTransformer: ScalaTranslatorExtension => (STContext, I, D) => D
                                            ): D = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, id, acc)
    }
  }

  def extend[I, D <: Defn, C <: Defn]
  (
    id: I
    , entity: D
    , companion: C
    , entityTransformer: ScalaTranslatorExtension => (STContext, I, D) => D
    , companionTransformer: ScalaTranslatorExtension => (STContext, I, C) => C
  ): Seq[Defn] = {
    val extendedEntity = extend(id, entity, entityTransformer)
    val extendedCompanion = extend(id, companion, companionTransformer)
    Seq(
      extendedEntity
      , extendedCompanion
    )
  }

}
