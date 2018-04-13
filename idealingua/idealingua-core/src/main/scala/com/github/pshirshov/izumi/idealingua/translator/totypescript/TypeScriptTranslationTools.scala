package com.github.pshirshov.izumi.idealingua.translator.totypescript

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

import scala.meta._


class TypeScriptTranslationTools(ctx: TSTContext) {
//
//  def mkStructure(id: StructureId): CompositeStructure = {
//    val fields = ctx.typespace.structure.structure(id).toTypescript
//    new CompositeStructure(ctx, fields)
//  }

  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
