package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.{StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.TranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslationContext

import scala.meta.Defn


trait ScalaTranslatorExtension extends TranslatorExtension {
  def handleModules(context: ScalaTranslationContext, acc: Seq[Module]): Seq[Module] = acc

  def handleComposite(context: ScalaTranslationContext, id: StructureId, defn: Defn.Class): Defn.Class = defn
  def handleCompositeCompanion(context: ScalaTranslationContext, id: StructureId, defn: Defn.Object): Defn.Object = defn
  def handleCompositeTools(context: ScalaTranslationContext, id: StructureId, defn: Defn.Class): Defn.Class = defn

  def handleIdentifier(context: ScalaTranslationContext, id: IdentifierId, defn: Defn.Class): Defn.Class = defn
  def handleIdentifierCompanion(context: ScalaTranslationContext, id: IdentifierId, defn: Defn.Object): Defn.Object = defn
  def handleIdentifierTools(context: ScalaTranslationContext, id: IdentifierId, defn: Defn.Class): Defn.Class = defn

  def handleAdt(context: ScalaTranslationContext, id: AdtId, defn: Defn.Trait): Defn.Trait = defn
  def handleAdtCompanion(context: ScalaTranslationContext, id: AdtId, defn: Defn.Object): Defn.Object = defn

  def handleAdtElement(context: ScalaTranslationContext, id: DTOId, defn: Defn.Class): Defn.Class = defn
  def handleAdtElementCompanion(context: ScalaTranslationContext, id: DTOId, defn: Defn.Object): Defn.Object = defn

  def handleEnum(context: ScalaTranslationContext, id: EnumId, defn: Defn.Trait): Defn.Trait = defn
  def handleEnumElement(context: ScalaTranslationContext, id: EnumId, defn: Defn): Defn = defn
  def handleEnumCompanion(context: ScalaTranslationContext, id: EnumId, defn: Defn.Object): Defn.Object = defn

  def handleInterface(context: ScalaTranslationContext, id: InterfaceId, defn: Defn.Trait): Defn.Trait = defn
  def handleInterfaceCompanion(context: ScalaTranslationContext, id: InterfaceId, defn: Defn.Object): Defn.Object = defn
  def handleInterfaceTools(context: ScalaTranslationContext, id: InterfaceId, defn: Defn.Class): Defn.Class = defn

  def handleService(context: ScalaTranslationContext, id: ServiceId, defn: Defn.Trait): Defn.Trait = defn
  def handleServiceCompanion(context: ScalaTranslationContext, id: ServiceId, defn: Defn.Object): Defn.Object = defn
  def handleServiceTools(context: ScalaTranslationContext, id: ServiceId, defn: Defn.Class): Defn.Class = defn

}


