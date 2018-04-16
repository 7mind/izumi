package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


class TypespaceImpl(val domain: DomainDefinition) extends Typespace with TypeResolver {
  protected[typespace] lazy val types: TypeCollection = new TypeCollection(domain)
  protected lazy val referenced: Map[DomainId, Typespace] = domain.referenced.mapValues(d => new TypespaceImpl(d))
  protected lazy val index: Map[TypeId, TypeDef] = types.index


  override lazy val inheritance: InheritanceQueries = new InheritanceQueriesImpl(this, types)

  override lazy val structure: StructuralQueries = new StructuralQueriesImpl(types, this, inheritance)

  override def implId(id: InterfaceId): DTOId = DTOId(id, types.toDtoName(id))

  def toDtoName(id: TypeId): String = types.toDtoName(id)

  def apply(id: ServiceId): Service = {
    types.services(id)
  }

  def apply(id: TypeId): TypeDef = {
    val typeDomain = domain.id.toDomainId(id)
    if (domain.id == typeDomain) {
      id match {
        case o =>
          index(o)
      }
    } else {
      referenced(typeDomain).apply(id)
    }
  }

  def verify(): Unit = {
    val typeDependencies = domain.types.flatMap(extractDependencies)

    val serviceDependencies = for {
      service <- types.services.values
      method <- service.methods
    } yield {
      method match {
        case m: RPCMethod =>
//        case m: DeprecatedRPCMethod =>
//          (m.signature.input ++ m.signature.output).map(i => DefinitionDependency.DepServiceParameter(service.id, i))
          Seq() // TODO
      }
    }

    val allDependencies = typeDependencies ++ serviceDependencies.flatten

    // TODO: very ineffective!
    val missingTypes = allDependencies
      .filterNot(_.typeId.isInstanceOf[Builtin])
      .filterNot(d => types.index.contains(d.typeId))
      .filterNot(d => referenced.get(domain.id.toDomainId(d.typeId)).exists(t => t.types.index.contains(d.typeId)))

    if (missingTypes.nonEmpty) {
      throw new IDLException(s"Incomplete typespace: $missingTypes")
    }

    domain.types.foreach {
      case t: TypeDef.Enumeration =>
        val duplicates = t.members.groupBy(v => v).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          throw new IDLException(s"Duplicated enum elements: $duplicates")
        }
      case t: TypeDef.Adt =>
        val duplicates = t.alternatives.groupBy(v => v.name).filter(_._2.lengthCompare(1) > 0)
        if (duplicates.nonEmpty) {
          throw new IDLException(s"Duplicated adt elements: $duplicates")
        }

      case _ =>

    }
  }


  protected def extractDependencies(definition: TypeDef): Seq[DefinitionDependency] = {
    definition match {
      case _: Enumeration =>
        Seq.empty
      case d: Interface =>
        d.struct.superclasses.interfaces.map(i => DefinitionDependency.DepInterface(d.id, i)) ++
          d.struct.superclasses.concepts.flatMap(c => extractDependencies(apply(c))) ++
          d.struct.fields.map(f => DefinitionDependency.DepField(d.id, f.typeId, f))
      case d: DTO =>
        d.struct.superclasses.interfaces.map(i => DefinitionDependency.DepInterface(d.id, i)) ++
          d.struct.superclasses.concepts.flatMap(c => extractDependencies(apply(c))) ++
          d.struct.fields.map(f => DefinitionDependency.DepField(d.id, f.typeId, f))

      case d: Identifier =>
        d.fields.map(f => DefinitionDependency.DepPrimitiveField(d.id, f.typeId, f))

      case d: Adt =>
        d.alternatives.map(_.typeId).map(apply).flatMap(extractDependencies)

      case d: Alias =>
        Seq(DefinitionDependency.DepAlias(d.id, d.target))
    }
  }


}



