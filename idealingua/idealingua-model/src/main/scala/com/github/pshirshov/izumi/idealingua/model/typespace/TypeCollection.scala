package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

class CMap[K, V](context: AnyRef, val underlying: Map[K, V]) {
  def contains(key: K): Boolean = underlying.contains(key)

  def fetch(k: K): V = {
    underlying.get(k) match {
      case Some(v) => v
      case None => throw new IDLException(s"Missing value in context $context: $k")
    }
  }
}

class TypeCollection(ts: Typespace) {
  val services: Map[ServiceId, Service] = ts.domain.services.groupBy(_.id).mapValues(_.head).toMap // 2.13 compat
  val buzzers: Map[BuzzerId, Buzzer] = ts.domain.buzzers.groupBy(_.id).mapValues(_.head).toMap // 2.13 compat

  val serviceEphemerals: Seq[TypeDef] = {
    makeServiceEphemerals(services.values )
  }

  val buzzerEphemerals: Seq[TypeDef] = {
    makeServiceEphemerals(buzzers.values.map(_.asService))
  }

  private def makeServiceEphemerals(src: Iterable[Service]): Seq[TypeDef] = {
    (for {
      service <- src
      method <- service.methods
    } yield {
      method match {
        case m: RPCMethod =>
          val baseName = m.name.capitalize

          val inputDto = {
            val in = m.signature.input
            val inputStructure = Structure.apply(in.fields, List.empty, Super(List.empty, in.concepts, List.empty))
            val inId = DTOId(service.id, s"$baseName${ts.tools.methodInputSuffix}")
            DTO(inId, inputStructure, NodeMeta.empty)
          }

          val outDtos = outputEphemeral(service.id, baseName, ts.tools.methodOutputSuffix, m.signature.output)

          inputDto +: outDtos
      }
    }).flatten.toSeq
  }

  private def outputEphemeral(serviceId: ServiceId, baseName: String, suffix: String, out: Output): Seq[TypeDef] = {
    out match {
      case o: Output.Singular =>
        val outStructure = Structure.apply(List(Field(o.typeId, "value")), List.empty, Super.empty)
        val outId = DTOId(serviceId, s"$baseName$suffix")
        Seq(DTO(outId, outStructure, NodeMeta.empty))

      case o: Output.Struct =>
        val outStructure = Structure.apply(o.struct.fields, List.empty, Super(List.empty, o.struct.concepts, List.empty))
        val outId = DTOId(serviceId, s"$baseName$suffix")
        Seq(DTO(outId, outStructure, NodeMeta.empty))

      case _: Output.Void =>
        val outStructure = Structure.apply(List.empty, List.empty, Super(List.empty, List.empty, List.empty))
        val outId = DTOId(serviceId, s"$baseName$suffix")
        Seq(DTO(outId, outStructure, NodeMeta.empty))

      case o: Output.Algebraic =>
        val outId = AdtId(serviceId, s"$baseName$suffix")
        Seq(Adt(outId, o.alternatives, NodeMeta.empty))

      case o: Output.Alternative =>
        val success = outputEphemeral(serviceId, baseName, ts.tools.goodAltSuffix, o.success)
        val failure = outputEphemeral(serviceId, baseName, ts.tools.badAltSuffix, o.failure)
        val successId = success.head.id
        val failureId = failure.head.id
        val adtId = AdtId(serviceId, s"$baseName$suffix")
        val altAdt = Output.Algebraic(List(
          AdtMember(successId, Some(ts.tools.toPositiveBranchName(adtId)))
            , AdtMember(failureId, Some(ts.tools.toNegativeBranchName(adtId)))
        ))
        val alt = outputEphemeral(serviceId, baseName, suffix, altAdt)
        success ++ failure ++ alt
    }
  }

  val interfaceEphemeralIndex: Map[InterfaceId, DTO] = {
    ts.domain.types
      .collect {
        case i: Interface =>
          val iid = DTOId(i.id, ts.tools.toDtoName(i.id))
          i.id -> DTO(iid, Structure.interfaces(List(i.id)), NodeMeta.empty)
      }.toMap
  }

  val interfaceEphemeralsReversed: Map[DTOId, InterfaceId] = {
    interfaceEphemeralIndex.map(kv => kv._2.id -> kv._1)
  }

  def isInterfaceEphemeral(dto: DTOId): Boolean = interfaceEphemeralsReversed.contains(dto)

  val dtoEphemeralIndex: Map[DTOId, Interface] = {
    (ts.domain.types ++ serviceEphemerals ++ buzzerEphemerals)
      .collect {
        case i: DTO =>
          val iid = InterfaceId(i.id, ts.tools.toInterfaceName(i.id))
          i.id -> Interface(iid, i.struct, NodeMeta.empty)
      }.toMap

  }

  val interfaceEphemerals: Seq[DTO] = interfaceEphemeralIndex.values.toSeq

  val dtoEphemerals: Seq[Interface] = dtoEphemeralIndex.values.toSeq

  val all: Seq[TypeDef] = {
    val definitions = Seq(
      ts.domain.types
      , serviceEphemerals
      , buzzerEphemerals
      , interfaceEphemerals
      , dtoEphemerals
    ).flatten

    verified(definitions)
  }

  val structures: Seq[WithStructure] = all.collect { case t: WithStructure => t }

  def domainIndex: Map[TypeId, TypeDef] = {
    ts.domain.types.map(t => (t.id, t)).toMap
  }

  def index: CMap[TypeId, TypeDef] = {
    new CMap(ts.domain.id, all.map(t => (t.id, t)).toMap)
  }

  protected def verified(types: Seq[TypeDef]): Seq[TypeDef] = {
    val conflictingTypes = types.groupBy(id => (id.id.path, id.id.name)).filter(_._2.lengthCompare(1) > 0)

    if (conflictingTypes.nonEmpty) {
      throw new IDLException(s"Conflicting types in: $conflictingTypes")
    }

    types
  }
}
