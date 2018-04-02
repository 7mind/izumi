package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.ExtendedField
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field

case class FieldConflicts private(
                                   all: Seq[ExtendedField]
                                   , goodFields: Map[String, Seq[ExtendedField]]
                                   , softConflicts: Map[String, Map[Field, Seq[ExtendedField]]]
                                   , hardConflicts: Map[String, Map[Field, Seq[ExtendedField]]]
                                 )

object FieldConflicts {
  def apply(fields: Seq[ExtendedField]): FieldConflicts = {
    val conflicts = fields
      .groupBy(_.field.name)

    val (goodFields: Map[String, Seq[ExtendedField]], conflictingFields) = conflicts.partition(_._2.lengthCompare(1) == 0)

    val (softConflicts: Map[String, Map[Field, Seq[ExtendedField]]], hardConflicts: Map[String, Map[Field, Seq[ExtendedField]]]) = conflictingFields
      .map(kv => (kv._1, kv._2.groupBy(_.field)))
      .partition(_._2.size == 1)

    FieldConflicts(fields, goodFields, softConflicts, hardConflicts)
  }
}
