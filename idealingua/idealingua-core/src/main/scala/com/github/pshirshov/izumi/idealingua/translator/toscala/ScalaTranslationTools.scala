package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, AliasId, EnumId, IdentifierId}
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst
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

  def withAnyval(struct: PlainScalaStruct, ifDecls: List[Init]): List[Init] = {
    doModify(ifDecls, "AnyVal", struct.all.size == 1)
  }


  def withAnyval(struct: Struct, ifDecls: List[Init]): List[Init] = {
    addAnyBase(struct, ifDecls, "AnyVal")
  }

  def withAny(struct: Struct, ifDecls: List[Init]): List[Init] = {
    addAnyBase(struct, ifDecls, "Any")
  }

  private def addAnyBase(struct: Struct, ifDecls: List[Init], base: String): List[Init] = {
    doModify(ifDecls, base, struct.isScalar && struct.all.forall(f => canBeAnyValField(f.field.typeId)))
  }

  private def canBeAnyValField(typeId: TypeId): Boolean = {
    typeId match {
      case _: Builtin =>
        true

      case _: EnumId =>
        true

      case _: AdtId =>
        false


      case a: AliasId =>
        ctx.typespace(a) match {
          case alias: ILAst.Alias =>
            canBeAnyValField(alias.target)

          case v =>
            throw new IDLException(s"Impossible case: $v cannot be an alias")
        }

      case t: StructureId =>
        val struct = ctx.typespace.enumFields(t)
        struct.isComposite || (struct.isScalar && !struct.all.exists(v => canBeAnyValField(v.field.typeId)))
      case t: IdentifierId =>
        val struct = ctx.typespace.structure(t)
        struct.all.size > 1

    }
  }

  private def doModify(ifDecls: List[Init], base: String, modify: Boolean) = {
    if (modify) {
      ctx.conv.toScala(JavaType(Seq.empty, base)).init() +: ifDecls
    } else {
      ifDecls
    }
  }
}
