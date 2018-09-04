package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.JavaType
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, AliasId, EnumId, IdentifierId}
import com.github.pshirshov.izumi.idealingua.model.common.{Builtin, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.{PlainStruct, Struct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProduct, IdentifierProudct}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{ScalaStruct, StructContext}

import scala.collection.mutable
import scala.meta._

object AnyvalExtension extends ScalaTranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._


  override def handleTrait(ctx: STContext, interface: ScalaStruct, product: CogenProduct.TraitProduct): CogenProduct.TraitProduct = {
    product.copy(defn = product.defn.prependBase(withAny(ctx, interface.fields)))
  }

  override def handleComposite(ctx: STContext, struct: StructContext, product: CompositeProduct): CompositeProduct = {
    product.copy(defn = product.defn.prependBase(withAnyval(ctx, struct.struct.fields)))
  }

  override def handleIdentifier(ctx: STContext, id: TypeDef.Identifier, product: IdentifierProudct): IdentifierProudct = {
    val struct = ctx.typespace.structure.structure(id.id)
    product.copy(defn = product.defn.prependBase(withAnyval(ctx, struct)))
  }

  private def withAnyval(ctx: STContext,struct: PlainStruct): List[Init] = {
    doModify(ctx, "AnyVal", struct.all.size == 1)
  }

  private def withAnyval(ctx: STContext,struct: Struct): List[Init] = {
    doModify(ctx, "AnyVal", struct.isScalar && struct.all.forall(f => canBeAnyValField(ctx, f.field.typeId)))
  }

  private def withAny(ctx: STContext,struct: Struct): List[Init] = {
    doModify(ctx, "Any", (struct.isScalar || struct.isEmpty) && struct.all.forall(f => canBeAnyValField(ctx, f.field.typeId)))
  }

  private def doModify(ctx: STContext, base: String, modify: Boolean): List[Init] = {
    if (modify) {
      List(ctx.conv.toScala(JavaType(Seq.empty, base)).init())
    } else {
      List.empty
    }
  }

  private def canBeAnyValField(ctx: STContext, typeId: TypeId): Boolean = {
    canBeAnyValField(ctx, typeId, mutable.HashSet.empty)
  }

  private def canBeAnyValField(ctx: STContext, typeId: TypeId, seen: mutable.HashSet[TypeId]): Boolean = {
    typeId match {
      case _: Builtin =>
        true

      case _: EnumId =>
        true

      case _: AdtId =>
        false


      case a: AliasId =>
        ctx.typespace(a) match {
          case alias: TypeDef.Alias =>
            canBeAnyValField(ctx, alias.target, seen + alias.target)

          case v =>
            throw new IDLException(s"Impossible case: $v cannot be an alias")
        }

      case t: StructureId =>
        val struct = ctx.typespace.structure.structure(t)
        struct.isComposite

        // this predicate doesn't work well across domains
        /*
        || (struct.isScalar && !struct.all
          .filterNot(v => seen.contains(v.field.typeId))
          .exists(v => canBeAnyValField(ctx, v.field.typeId, seen + v.field.typeId)))
          */

      case t: IdentifierId =>
        val struct = ctx.typespace.structure.structure(t)
        struct.all.size > 1

    }
  }
}
