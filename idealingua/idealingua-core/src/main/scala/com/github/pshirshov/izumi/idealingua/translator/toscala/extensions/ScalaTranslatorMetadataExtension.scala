package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{IdentifierId, ServiceId}
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, IndefiniteId, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinition
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslationContext

object ScalaTranslatorMetadataExtension extends ScalaTranslatorExtension {

  import scala.meta._

  override def handleComposite(context: ScalaTranslationContext, id: StructureId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }

  override def handleIdentifier(context: ScalaTranslationContext, id: IdentifierId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }


  override def handleAdtElement(context: ScalaTranslationContext, id: TypeId.DTOId, defn: Defn.Class): Defn.Class = {
    withInfo(context, id, defn)
  }

  override def handleEnumElement(context: ScalaTranslationContext, id: TypeId.EnumId, defn: Defn): Defn = {
    withInfo(context, id, defn)
  }

  override def handleService(context: ScalaTranslationContext, id: TypeId.ServiceId, defn: Defn.Trait): Defn.Trait = {
    ???
    //withInfo(context, id, defn)
  }

  private def withInfo[T <: Defn](context: ScalaTranslationContext, id: TypeId, defn: T): T = {
    withInfo(context, id, defn, id)
  }


  override def handleInterfaceTools(context: ScalaTranslationContext, id: TypeId.InterfaceId, defn: Defn.Class): Defn.Class = {
    //             ${rt.modelConv.toMethodAst(i.id)}
    ???
  }


  override def handleServiceTools(context: ScalaTranslationContext, id: ServiceId, defn: Defn.Class): Defn.Class = {
    // ${rt.modelConv.toMethodAst(IndefiniteId(i.id))}
    ???
  }


  override def handleIdentifierTools(context: ScalaTranslationContext, id: IdentifierId, defn: Defn.Class): Defn.Class = {
    // ${rt.modelConv.toMethodAst(i.id)}
    ???
  }


  override def handleCompositeTools(context: ScalaTranslationContext, id: StructureId, defn: Defn.Class): Defn.Class = {
    //                 ${ctx.rt.modelConv.toMethodAst(id)}
    ???
  }

//  def toMethodAst[T <: TypeId : ClassTag](typeId: T): Defn.Def = {
//    toMethodAst(IndefiniteId(typeId))
//  }
//
//  private def toMethodAst(typeId: IndefiniteId): Defn.Def = {
//    val tpe = toSelect(JavaType(typeId).minimize(domain))
//    q"def toTypeId: $tpe = { ${toAst(typeId)} }"
//  }
//
//
//  def toAst(typeId: IndefiniteId): Term.Apply = {
//    toIdConstructor(typeId)
//  }
//
//  private def toIdConstructor(t: IndefiniteId): Term.Apply = {
//    q"${toSelectTerm(JavaType(t).minimize(domain))}(Seq(..${t.pkg.map(Lit.String.apply).toList}), ${Lit.String(t.name)})"
//  }
//
//  def toIdConstructor(t: DomainId): Term.Apply = {
//    q"${toSelectTerm(JavaType.get[DomainId])}(Seq(..${t.pkg.map(Lit.String.apply).toList}), ${Lit.String(t.id)})"
//  }

  def domainCompanionId(domainDefinition: DomainDefinition): IndefiniteId = {
    IndefiniteId(Seq("izumi", "idealingua", "domains"), domainDefinition.id.id.capitalize)
  }

  private def withInfo[T <: Defn](context: ScalaTranslationContext, id: AbstractTypeId, defn: T, sigId: TypeId): T = {
    import context._
    //${rt.modelConv.toAst(id)}
    val domainsDomain = domainCompanionId(typespace.domain)
    val tDomain = conv.toScala(domainsDomain)

    val stats = List(
      q"""def _info: ${rt.typeInfo.typeFull} = {
          ${rt.typeInfo.termFull}(
            ???
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
    //    import context._
    //    val index = typespace.all.map(id => id -> conv.toScala(id)).toList
    //
    //    val exprs = index.map {
    //      case (k@ServiceId(_, _), v) =>
    //        rt.modelConv.toAst(k) -> q"classOf[${v.parameterize("Id").typeFull}]"
    //      case (k, v) =>
    //        rt.modelConv.toAst(k) -> q"classOf[${v.typeFull}]"
    //    }
    //
    //    val types = exprs.map({ case (k, v) => q"$k -> $v" })
    //    val reverseTypes = exprs.map({ case (k, v) => q"$v -> $k" })
    //
    //    val schema = schemaSerializer.serializeSchema(typespace.domain)
    //
    //    val references = typespace.domain.referenced.toList.map {
    //      case (k, v) =>
    //        q"${conv.toIdConstructor(k)} -> ${conv.toScala(conv.domainCompanionId(v)).termFull}.schema"
    //    }
    //
    //    modules.toSource(domainsDomain, ModuleId(domainsDomain.pkg, s"${domainsDomain.name}.scala"), Seq(
    //      q"""object ${tDomain.termName} extends ${rt.tDomainCompanion.init()} {
    //         ${conv.toImport}
    //
    //         type Id[T] = T
    //
    //         lazy val id: ${conv.toScala[DomainId].typeFull} = ${conv.toIdConstructor(typespace.domain.id)}
    //         lazy val types: Map[${rt.typeId.typeFull}, Class[_]] = Seq(..$types).toMap
    //         lazy val classes: Map[Class[_], ${rt.typeId.typeFull}] = Seq(..$reverseTypes).toMap
    //         lazy val referencedDomains: Map[${rt.tDomainId.typeFull}, ${rt.tDomainDefinition.typeFull}] = Seq(..$references).toMap
    //
    //         protected lazy val serializedSchema: String = ${Lit.String(schema)}
    //       }"""
    //    ))
    ???
  }
}
