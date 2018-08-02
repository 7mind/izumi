package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId, InterfaceId, ServiceId}
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

class TypeCollection(domain: DomainDefinition) {
  val services: Map[ServiceId, Service] = domain.services.groupBy(_.id).mapValues(_.head).toMap // 2.13 compat

  val serviceEphemerals: Seq[TypeDef] = (for {
    service <- services.values
    method <- service.methods
  } yield {
    method match {
      case m: RPCMethod =>
        val baseName = m.name.capitalize

        val inputDto = {
          val in = m.signature.input
          val inputStructure = Structure.apply(in.fields, List.empty, Super(List.empty, in.concepts, List.empty))
          val inId = DTOId(service.id, s"${baseName}Input")
          DTO(inId, inputStructure, None)
        }


        val outDto = m.signature.output match {
          case o: Output.Singular =>
            val outStructure = Structure.apply(List(Field(o.typeId, "value")), List.empty, Super.empty)
            val outId = DTOId(service.id, s"${baseName}Output")
            DTO(outId, outStructure, None)

          case o: Output.Struct =>
            val outStructure = Structure.apply(o.struct.fields, List.empty, Super(List.empty, o.struct.concepts, List.empty))
            val outId = DTOId(service.id, s"${baseName}Output")
            DTO(outId, outStructure, None)

          case _: Output.Void =>
            val outStructure = Structure.apply(List.empty, List.empty, Super(List.empty, List.empty, List.empty))
            val outId = DTOId(service.id, s"${baseName}Output")
            DTO(outId, outStructure, None)

          case o: Output.Algebraic =>
            val outId = AdtId(service.id, s"${baseName}Output")
            Adt(outId, o.alternatives, None)
        }

        Seq(inputDto, outDto)
    }
  }).flatten.toSeq

  val interfaceEphemeralIndex: Map[InterfaceId, DTO] = {
    domain.types
      .collect {
        case i: Interface =>
          val iid = DTOId(i.id, toDtoName(i.id))
          i.id -> DTO(iid, Structure.interfaces(List(i.id)), None)
      }.toMap
  }

  val interfaceEphemeralsReversed: Map[DTOId, InterfaceId] = {
    interfaceEphemeralIndex.map(kv => kv._2.id -> kv._1)
  }

  def isInterfaceEphemeral(dto: DTOId): Boolean = interfaceEphemeralsReversed.contains(dto)

  val dtoEphemeralIndex: Map[DTOId, Interface] = {
    (domain.types ++ serviceEphemerals)
      .collect {
        case i: DTO =>
          val iid = InterfaceId(i.id, toInterfaceName(i.id))
          i.id -> Interface(iid, i.struct, None)
      }.toMap

  }

  val interfaceEphemerals: Seq[DTO] = interfaceEphemeralIndex.values.toSeq

  val dtoEphemerals: Seq[Interface] = dtoEphemeralIndex.values.toSeq

  val all: Seq[TypeDef] = {
    val definitions = Seq(
      domain.types
      , serviceEphemerals
      , interfaceEphemerals
      , dtoEphemerals
    ).flatten

    verified(definitions)
  }

  val structures: Seq[WithStructure] = all.collect { case t: WithStructure => t }

  def domainIndex: Map[TypeId, TypeDef] = {
    domain.types.map(t => (t.id, t)).toMap
  }

  def index: CMap[TypeId, TypeDef] = {
    new CMap(domain.id, all.map(t => (t.id, t)).toMap)
  }

  def toDtoName(id: TypeId): String = {
    id match {
      case _: InterfaceId =>
        //s"${id.name}Struct"
        s"Struct"
      case _ =>
        s"${id.name}"

    }
  }

  def toInterfaceName(id: TypeId): String = {
    id match {
      case _: DTOId =>
        //s"${id.name}Defn"

        s"Defn"
      case _ =>
        s"${id.name}"

    }
  }

  protected def verified(types: Seq[TypeDef]): Seq[TypeDef] = {
    val conflictingTypes = types.groupBy(id => (id.id.path, id.id.name)).filter(_._2.lengthCompare(1) > 0)

    if (conflictingTypes.nonEmpty) {
      throw new IDLException(s"Conflicting types in: $conflictingTypes")
    }

    types
  }
}
