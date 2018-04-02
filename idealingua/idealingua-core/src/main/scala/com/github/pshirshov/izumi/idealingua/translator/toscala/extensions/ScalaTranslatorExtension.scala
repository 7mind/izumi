package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct


trait ScalaTranslatorExtension extends TranslatorExtension {

  import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct._

  def handleModules(ctx: STContext, acc: Seq[Module]): Seq[Module] = acc

  def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = product
  def handleComposite(ctx: STContext, struct: ScalaStruct, product: CompositeProudct): CompositeProudct = product
  def handleIdentifier(ctx: STContext, id: Identifier, product: IdentifierProudct): IdentifierProudct = product
  def handleService(ctx: STContext, service: Service, product: ServiceProudct): ServiceProudct = product
  def handleEnum(ctx: STContext, enum: Enumeration, product: EnumProduct): EnumProduct = product
  def handleAdt(ctx: STContext, adt: Adt, product: AdtProduct): AdtProduct = product
}


