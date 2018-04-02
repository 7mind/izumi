package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, AliasId, EnumId, IdentifierId}
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.il.structures.Struct
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{CompositeStructure, PlainScalaStruct}

import scala.meta._


class ScalaTranslationTools(ctx: STContext) {
  import ctx.conv._

  def mkStructure(id: StructureId): CompositeStructure = {
    val fields = ctx.typespace.enumFields(id).toScala
    new CompositeStructure(ctx, fields)
  }


  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
