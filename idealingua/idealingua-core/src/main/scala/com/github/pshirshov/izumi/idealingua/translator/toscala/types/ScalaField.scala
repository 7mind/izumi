package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.ExtendedField

import scala.meta.{Term, Type}

case class ScalaField(name: Term.Name, fieldType: Type, field: ExtendedField, conflicts: Set[ExtendedField])

object ScalaField {
  implicit class ScalaFieldsExt(fields: TraversableOnce[ScalaField]) {
    def toParams: List[Term.Param] = fields.map(f => (f.name, f.fieldType)).toParams

    def toNames: List[Term.Name] = fields.map(_.name).toList
  }

  implicit class NamedTypeExt(fields: TraversableOnce[(Term.Name, Type)]) {
    def toParams: List[Term.Param] = fields.map(f => (f._1, f._2)).map(toParam).toList
  }

  private def toParam(p: (Term.Name, Type)): Term.Param = {
    Term.Param(List.empty, p._1, Some(p._2), None)
  }
}
