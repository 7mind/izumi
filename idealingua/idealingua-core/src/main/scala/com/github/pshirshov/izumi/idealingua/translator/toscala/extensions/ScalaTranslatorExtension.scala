package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaStruct

import scala.meta.Defn


trait ScalaTranslatorExtension extends TranslatorExtension {
  def handleModules(ctx: STContext, acc: Seq[Module]): Seq[Module] = acc

  def handleComposite(ctx: STContext, struct: ScalaStruct, defn: Defn.Class): Defn.Class = defn
  def handleCompositeCompanion(ctx: STContext, struct: ScalaStruct, defn: Defn.Object): Defn.Object = defn
  def handleCompositeTools(ctx: STContext, struct: ScalaStruct, defn: Defn.Class): Defn.Class = defn

  def handleInterface(ctx: STContext, iface: Interface, defn: Defn.Trait): Defn.Trait = defn
  def handleInterfaceCompanion(ctx: STContext, iface: Interface, defn: Defn.Object): Defn.Object = defn
  def handleInterfaceTools(ctx: STContext, iface: Interface, defn: Defn.Class): Defn.Class = defn

  def handleIdentifier(ctx: STContext, id: Identifier, defn: Defn.Class): Defn.Class = defn
  def handleIdentifierCompanion(ctx: STContext, id: Identifier, defn: Defn.Object): Defn.Object = defn
  def handleIdentifierTools(ctx: STContext, id: Identifier, defn: Defn.Class): Defn.Class = defn

  def handleAdt(ctx: STContext, adt: Adt, defn: Defn.Trait): Defn.Trait = defn
  def handleAdtCompanion(ctx: STContext, adt: Adt, defn: Defn.Object): Defn.Object = defn

  def handleAdtElement(ctx: STContext, id: DTOId, defn: Defn.Class): Defn.Class = defn
  def handleAdtElementCompanion(ctx: STContext, id: DTOId, defn: Defn.Object): Defn.Object = defn

  def handleEnum(ctx: STContext, enum: Enumeration, defn: Defn.Trait): Defn.Trait = defn
  def handleEnumElement(ctx: STContext, enum: Enumeration, defn: Defn): Defn = defn
  def handleEnumCompanion(ctx: STContext, enum: Enumeration, defn: Defn.Object): Defn.Object = defn

  def handleService(ctx: STContext, id: Service, defn: Defn.Trait): Defn.Trait = defn
  def handleServiceCompanion(ctx: STContext, id: Service, defn: Defn.Object): Defn.Object = defn
  def handleServiceTools(ctx: STContext, id: Service, defn: Defn.Class): Defn.Class = defn

}


