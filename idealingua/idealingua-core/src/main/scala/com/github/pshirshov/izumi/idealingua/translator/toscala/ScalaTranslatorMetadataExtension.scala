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

  override def handleEnumElement(context: ScalaTranslationContext, id: TypeId.EnumId, defn: Defn): Defn = {
    withInfo(context, id, defn)
  }

  override def handleService(context: ScalaTranslationContext, id: TypeId.ServiceId, defn: Defn.Trait): Defn.Trait = {
    withInfo(context, id, defn)
  }

  private def withInfo[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T): T = {
    withInfo(context, id, defn, id)
  }

  private def withInfo[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T, sigId: TypeId): T = {
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
    defn.extendDefinition(stats).addBase(List(rt.withTypeInfo.init()))
  }

}
