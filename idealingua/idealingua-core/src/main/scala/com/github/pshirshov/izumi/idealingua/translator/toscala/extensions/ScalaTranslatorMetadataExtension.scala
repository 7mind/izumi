package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{EnumId, EphemeralId, IdentifierId, ServiceId}
import com.github.pshirshov.izumi.idealingua.model.il.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslationContext

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
            ${rt.modelConv.toAst(id)}
            , ${tDomain.termFull}
            , ${Lit.Int(sig.signature(sigId))}
          ) }"""
    )

    import com.github.pshirshov.izumi.idealingua.translator.toscala.tools.ScalaMetaTools._
    defn.extendDefinition(stats).addBase(List(rt.withTypeInfo.init()))
  }


  override def handleModules(context: ScalaTranslationContext, acc: Seq[Module]): Seq[Module] = {
    acc ++ translateDomain(context)
  }

  private def translateDomain(context: ScalaTranslationContext): Seq[Module] = {
    import context._
    val index = typespace.all.map(id => id -> conv.toScala(id)).toList

    val exprs = index.map {
      case (k@EphemeralId(_: EnumId, _), v) =>
        rt.modelConv.toAst(k) -> q"${v.termBase}.getClass"
      case (k@ServiceId(_, _), v) =>
        rt.modelConv.toAst(k) -> q"classOf[${v.parameterize("Id").typeFull}]"
      case (k, v) =>
        rt.modelConv.toAst(k) -> q"classOf[${v.typeFull}]"
    }

    val types = exprs.map({ case (k, v) => q"$k -> $v" })
    val reverseTypes = exprs.map({ case (k, v) => q"$v -> $k" })

    val schema = schemaSerializer.serializeSchema(typespace.domain)

    val references = typespace.domain.referenced.toList.map {
      case (k, v) =>
        q"${conv.toIdConstructor(k)} -> ${conv.toScala(conv.domainCompanionId(v)).termFull}.schema"
    }

    modules.toSource(domainsDomain, ModuleId(domainsDomain.pkg, s"${domainsDomain.name}.scala"), Seq(
      q"""object ${tDomain.termName} extends ${rt.tDomainCompanion.init()} {
         ${conv.toImport}

         type Id[T] = T

         lazy val id: ${conv.toScala[DomainId].typeFull} = ${conv.toIdConstructor(typespace.domain.id)}
         lazy val types: Map[${rt.typeId.typeFull}, Class[_]] = Seq(..$types).toMap
         lazy val classes: Map[Class[_], ${rt.typeId.typeFull}] = Seq(..$reverseTypes).toMap
         lazy val referencedDomains: Map[${rt.tDomainId.typeFull}, ${rt.tDomainDefinition.typeFull}] = Seq(..$references).toMap

         protected lazy val serializedSchema: String = ${Lit.String(schema)}
       }"""
    ))
  }
}
