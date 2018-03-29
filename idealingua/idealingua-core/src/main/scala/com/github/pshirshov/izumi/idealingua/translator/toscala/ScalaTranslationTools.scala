package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, AliasId, EnumId}
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst
import com.github.pshirshov.izumi.idealingua.model.il.structures.Struct
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{CompositeStructure, ScalaStruct}

import scala.meta._


class ScalaTranslationTools(ctx: ScalaTranslationContext) {
  import ctx.conv._

  def mkStructure(id: StructureId): CompositeStructure = {
    val fields = ctx.typespace.enumFields(id).toScala
    new CompositeStructure(ctx, fields)
  }


  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)


  def withAnyval(struct: Struct, ifDecls: List[Init]): List[Init] = {
    addAnyBase(struct, ifDecls, "AnyVal")
  }

  def withAny(struct: Struct, ifDecls: List[Init]): List[Init] = {
    addAnyBase(struct, ifDecls, "Any")
  }

  def mkConverters(id: StructureId, struct: ScalaStruct): List[Defn.Def] = {
    val converters = ctx.typespace.sameSignature(id).map {
      same =>
        val code = struct.all.map {
          f =>
            q""" ${f.name} = _value.${f.name}  """
        }
        q"""def ${Term.Name("cast" + same.id.name.capitalize)}(): ${toScala(same.id).typeFull} = {
              ${toScala(same.id).termFull}(..$code)
            }
          """

    }
    converters
  }

  private def addAnyBase(struct: Struct, ifDecls: List[Init], base: String): List[Init] = {
    if (struct.isScalar && struct.all.forall(f => canBeAnyValField(f.field.typeId))) {
      ctx.conv.toScala(JavaType(Seq.empty, base)).init() +: ifDecls
    } else {
      ifDecls
    }
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
    }
  }
}
