package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.finaldef.FinalDefinition.{Alias, DTO, Identifier, Interface}

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

  def fetchFields(defn: FinalDefinition): List[Field] = {
    defn match {
      case t: Interface =>
        t.interfaces.flatMap(i => fetchFields(typespace(i))).toList ++ t.ownFields.toList

      case t: DTO =>
        t.interfaces.flatMap(i => fetchFields(typespace(i))).toList

      case t: Identifier =>
        t.fields.toList

      case t: Alias =>
        List()
    }
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
        t.interfaces.flatMap(i => explode(typespace(i))).toList ++ t.ownFields.flatMap(explode).toList

      case t: DTO =>
        t.interfaces.flatMap(i => explode(typespace(i))).toList

      case t: Identifier =>
        t.fields.flatMap(explode).toList

      case t: Alias =>
        List()
    }
  }

}
