package izumi.idealingua.model.typespace

import izumi.idealingua.model.common.TypeId
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.DefMethod.{Output, RPCMethod}
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.il.ast.typed._

class CMap[K, V](context: AnyRef, val underlying: Map[K, V]) {
  def contains(key: K): Boolean = underlying.contains(key)

  def fetch(k: K): V = {
    underlying.get(k) match {
      case Some(v) => v
      case None => throw new IDLException(s"Missing value in context $context: $k")
    }
  }
}

trait TypeCollectionData {
  def all: Seq[TypeDef]


  def structures: Seq[WithStructure]


  def interfaceEphemerals: Seq[DTO]

  def interfaceEphemeralIndex: Map[InterfaceId, DTO]

  def interfaceEphemeralsReversed: Map[DTOId, InterfaceId]


  def dtoEphemerals: Seq[Interface]

  def dtoEphemeralIndex: Map[DTOId, Interface]


  def services: Map[ServiceId, Service]

  def serviceEphemerals: Seq[TypeDef]


  def buzzers: Map[BuzzerId, Buzzer]

  def buzzerEphemerals: Seq[TypeDef]
}

class TypeCollection(ts: Typespace) extends TypeCollectionData {
  val services: Map[ServiceId, Service] = ts.domain.services.groupBy(_.id).mapValues(_.head).toMap // 2.13 compat
  val buzzers: Map[BuzzerId, Buzzer] = ts.domain.buzzers.groupBy(_.id).mapValues(_.head).toMap // 2.13 compat

  val serviceEphemerals: Seq[TypeDef] = {
    makeServiceEphemerals(services.values)
  }

  val buzzerEphemerals: Seq[TypeDef] = {
    makeServiceEphemerals(buzzers.values.map(_.asService))
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
        val outStructure = Structure.apply(List(Field(o.typeId, "value", NodeMeta.empty)), List.empty, Super.empty)
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
        val (success, successId) = toOutDef(serviceId, baseName, ts.tools.goodAltSuffix, o.success) //outputEphemeral(serviceId, baseName, ts.tools.goodAltSuffix, o.success)
      val (failure, failureId) = toOutDef(serviceId, baseName, ts.tools.badAltSuffix, o.failure) //outputEphemeral(serviceId, baseName, ts.tools.badAltSuffix, o.failure)
      val adtId = AdtId(serviceId, s"$baseName$suffix")
        val altAdt = Output.Algebraic(List(
          AdtMember(successId, Some(ts.tools.toPositiveBranchName(adtId)), NodeMeta.empty)
          , AdtMember(failureId, Some(ts.tools.toNegativeBranchName(adtId)), NodeMeta.empty)
        ))
        val alt = outputEphemeral(serviceId, baseName, suffix, altAdt)
        success ++ failure ++ alt
    }
  }

  private def toOutDef(serviceId: ServiceId, baseName: String, suffix: String, out: Output): (Seq[TypeDef], TypeId) = {
    out match {
      case o: Output.Singular =>
        (Seq.empty, o.typeId)
      case o =>
        // TODO: this is untested an probably incorrect!
        val res = outputEphemeral(serviceId, baseName, suffix, o)
        (res, res.head.id)
    }
  }

  def isInterfaceEphemeral(dto: DTOId): Boolean = interfaceEphemeralsReversed.contains(dto)


  def domainIndex: Map[TypeId, TypeDef] = {
    ts.domain.types.map(t => (t.id, t)).toMap
  }

  def index: CMap[TypeId, TypeDef] = {
    new CMap(ts.domain.id, all.map(t => (t.id, t)).toMap)
  }

  protected def verified(types: Seq[TypeDef]): Seq[TypeDef] = {
    val conflictingTypes = types.groupBy(tpe => (tpe.id.path, tpe.id.name)).filter(_._2.size > 1)

    if (conflictingTypes.nonEmpty) {
      import izumi.fundamentals.platform.strings.IzString._

      val formatted = conflictingTypes.map {
        case (tpe, conflicts) =>
          s"${tpe._1}${tpe._2}: ${conflicts.niceList().shift(2)}"
      }

      throw new IDLException(s"Conflicting types: ${formatted.niceList()}")
    }

    types
  }
}
