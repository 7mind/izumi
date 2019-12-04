package izumi.idealingua.translator.toscala.extensions

import izumi.idealingua.model.JavaType
import izumi.idealingua.model.common.TypeId.{AdtId, AliasId, EnumId, IdentifierId}
import izumi.idealingua.model.common.{Builtin, Generic, StructureId, TypeId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.model.typespace.structures.{PlainStruct, Struct}
import izumi.idealingua.translator.toscala.STContext
import izumi.idealingua.translator.toscala.products.CogenProduct
import izumi.idealingua.translator.toscala.products.CogenProduct.{CompositeProduct, IdentifierProudct}
import izumi.idealingua.translator.toscala.types.{ScalaStruct, StructContext}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.meta._

object AnyvalExtension extends ScalaTranslatorExtension {

  import izumi.idealingua.translator.toscala.tools.ScalaMetaTools._


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

  @tailrec
  private def canBeAnyValField(ctx: STContext, typeId: TypeId, /* unused */ seen: mutable.HashSet[TypeId]): Boolean = {
    typeId match {
      case _: Generic =>
        false // https://github.com/scala/bug/issues/11170

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
            throw new IDLException(s"Impossible case: $v cannot be anything but alias")
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
