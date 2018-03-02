package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

object ScalaTranslatorMetadataExtension extends ScalaTranslatorExtension {
  import scala.meta._

  override def handleComposite(context: ScalaTranslationContext, id: TypeId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }


  def withInfo[T <: Defn](context: ScalaTranslationContext,  id: TypeId, defn: T): T = {
    import context._
    val stats = List(
      q"""def _info: ${rt.typeInfo.typeFull} = {
          ${rt.typeInfo.termFull}(
            ${rt.conv.toAst(id)}
            , ${tDomain.termFull}
            , ${Lit.Int(sig.signature(id))}
          ) }"""
    )

    import ScalaMetaTools._
    defn.extendDefinition(stats)
  }

}
