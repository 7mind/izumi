package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct

import scala.meta.Defn


trait ScalaTranslatorExtension extends TranslatorExtension {

  import CogenProduct._

  def handleModules(ctx: STContext, acc: Seq[Module]): Seq[Module] = acc

  def handleInterface(ctx: STContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = product
  def handleComposite(ctx: STContext, struct: ScalaStruct, product: CompositeProudct): CompositeProudct = product
  def handleIdentifier(ctx: STContext, id: Identifier, product: IdentifierProudct): IdentifierProudct = product
  def handleService(ctx: STContext, service: Service, product: ServiceProudct): ServiceProudct = product
  def handleEnum(ctx: STContext, enum: Enumeration, product: EnumProduct): EnumProduct = product


  def handleAdt(ctx: STContext, adt: Adt, defn: Defn.Trait): Defn.Trait = defn

  def handleAdtCompanion(ctx: STContext, adt: Adt, defn: Defn.Object): Defn.Object = defn

  def handleAdtElement(ctx: STContext, id: DTOId, defn: Defn.Class): Defn.Class = defn

  def handleAdtElementCompanion(ctx: STContext, id: DTOId, defn: Defn.Object): Defn.Object = defn
}


