package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.{StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TSTContext

import scala.meta._


class TypeScriptTranslationTools(ctx: TSTContext) {
  import ctx.conv._
//
//  def mkStructure(id: StructureId): CompositeStructure = {
//    val fields = ctx.typespace.structure.structure(id).toTypescript
//    new CompositeStructure(ctx, fields)
//  }


  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
