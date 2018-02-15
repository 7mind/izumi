package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model._
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition._

import scala.annotation.tailrec

class Typespace(domain: DomainDefinition) {
  protected val typespace: Map[TypeId, FinalDefinition] = verified(domain.types)

  protected def verified(types: Seq[FinalDefinition]): Map[TypeId, FinalDefinition] = {
    val conflictingTypes = types.groupBy(_.id.name).filter(_._2.lengthCompare(1) > 0)
    if (conflictingTypes.nonEmpty) {
      throw new IDLException(s"Conflicting types in: $conflictingTypes")
    }
    types.groupBy(_.id).mapValues(_.head)
  }

  def apply(id: TypeId): FinalDefinition = typespace.apply(id)
  def apply(id: InterfaceId): Interface = typespace.apply(id).asInstanceOf[Interface]
  def apply(id: DTOId): DTO = typespace.apply(id).asInstanceOf[DTO]

  def implements(id: TypeId): List[InterfaceId] = {
    id match {
      case i: InterfaceId =>
        List(i) ++ apply(i).interfaces.toList.flatMap(implements)

      case i: DTOId =>
        apply(i).interfaces.toList.flatMap(implements)

      case _ =>
        List.empty
    }
  }

  def whoImplements(id: InterfaceId): List[DTOId] = {
    typespace.collect {
      case (tid: DTOId, d: DTO) if d.interfaces.contains(id)=>
        tid
    }.toList
  }

  def enumFields(composite: Composite): List[ExtendedField] = {
    composite.flatMap(i => enumFields(typespace(i))).toList
  }

  def enumFields(defn: FinalDefinition): List[ExtendedField] = {
    defn match {
      case t: Interface =>
        enumFields(t.interfaces) ++ toExtendedFields(t.fields, t.id)

      case t: DTO =>
        enumFields(t.interfaces)

      case t: Identifier =>
        toExtendedFields(t.fields, t.id)

      case t: Alias =>
        List()
    }
  }

  private def toExtendedFields(fields: Aggregate, id: TypeId) = {
    fields.map(f => ExtendedField(f, id: TypeId)).toList
  }

  def fetchFields(composite: Composite): List[Field] = {
    enumFields(composite).map(_.field)
  }

  def fetchFields(defn: FinalDefinition): List[Field] = {
    enumFields(defn).map(_.field)
  }


  // TODO: do we need this?
  def explode(defn: Field): List[TrivialField] = {
    defn.typeId match {
      case t: Primitive =>
        List(TrivialField(t, defn.name))
      case t: UserType =>
        explode(typespace(t))
    }
  }

  def explode(defn: FinalDefinition): List[TrivialField] = {
    defn match {
      case t: Interface =>
        t.interfaces.flatMap(i => explode(typespace(i))).toList ++ t.fields.flatMap(explode).toList

      case t: DTO =>
        t.interfaces.flatMap(i => explode(typespace(i))).toList

      case t: Identifier =>
        t.fields.flatMap(explode).toList

      case t: Alias =>
        List()
    }
  }

}
