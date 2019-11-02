package izumi.idealingua.translator.toscala.extensions

import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.output.Module
import izumi.idealingua.translator.TranslatorExtension
import izumi.idealingua.translator.toscala.STContext
import izumi.idealingua.translator.toscala.types.{FullServiceContext, ScalaStruct, StructContext}


trait ScalaTranslatorExtension extends TranslatorExtension {

  import izumi.fundamentals.platform.language.Quirks._
  import izumi.idealingua.translator.toscala.products.CogenProduct._

  def handleModules(ctx: STContext, acc: Seq[Module]): Seq[Module] = {
    discard(ctx)
    acc
  }

  def handleTrait(ctx: STContext, struct: ScalaStruct, product: TraitProduct): TraitProduct = {
    discard(ctx, struct)
    product
  }

  def handleComposite(ctx: STContext, struct: StructContext, product: CompositeProduct): CompositeProduct = {
    discard(ctx, struct)
    product
  }

  def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    discard(ctx, interface)
    product
  }

  def handleIdentifier(ctx: STContext, id: Identifier, product: IdentifierProudct): IdentifierProudct = {
    discard(ctx, id)
    product
  }

  def handleService(ctx: STContext, sCtx: FullServiceContext, product: CogenServiceProduct): CogenServiceProduct = {
    discard(ctx, sCtx)
    product
  }

  def handleEnum(ctx: STContext, enum: Enumeration, product: EnumProduct): EnumProduct = {
    discard(ctx, enum)
    product
  }

  def handleAdt(ctx: STContext, adt: Adt, product: AdtProduct): AdtProduct = {
    discard(ctx, adt)
    product
  }


}


