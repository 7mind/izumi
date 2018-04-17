package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenServiceProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{FullServiceContext, ScalaStruct, ServiceContext}


trait ScalaTranslatorExtension extends TranslatorExtension {
  import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
  import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct._

  def handleModules(ctx: STContext, acc: Seq[Module]): Seq[Module] = {
    discard(ctx)
    acc
  }

  def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    discard(ctx, interface)
    product
  }
  def handleComposite(ctx: STContext, struct: ScalaStruct, product: CompositeProudct): CompositeProudct = {
    discard(ctx, struct)
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


