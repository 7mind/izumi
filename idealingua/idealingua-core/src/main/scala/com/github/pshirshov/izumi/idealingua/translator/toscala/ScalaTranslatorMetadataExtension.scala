package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.IdentifierId

object ScalaTranslatorMetadataExtension extends ScalaTranslatorExtension {

  import scala.meta._

  override def handleComposite(context: ScalaTranslationContext, id: TypeId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }

  override def handleIdentifier(context: ScalaTranslationContext, id: IdentifierId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }


  override def handleAdtElement(context: ScalaTranslationContext, id: TypeId.EphemeralId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }

  override def handleAdt(context: ScalaTranslationContext, id: TypeId.AdtId, defn: Defn.Trait): Defn.Trait = {
    defn
  }

  override def handleEnum(context: ScalaTranslationContext, id: TypeId.EnumId, defn: Defn.Trait): Defn.Trait = {
    defn
  }

  override def handleInterface(context: ScalaTranslationContext, id: TypeId.InterfaceId, defn: Defn.Trait): Defn.Trait = {
    defn
  }

  def withInfo[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T): T = {
    withInfo(context, id, defn, id)
  }

  def withInfo[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T, sigId: TypeId): T = {
    import context._
    val stats = List(
      q"""def _info: ${rt.typeInfo.typeFull} = {
          ${rt.typeInfo.termFull}(
            ${rt.conv.toAst(id)}
            , ${tDomain.termFull}
            , ${Lit.Int(sig.signature(sigId))}
          ) }"""
    )

    import ScalaMetaTools._
    defn.extendDefinition(stats)
  }

}
