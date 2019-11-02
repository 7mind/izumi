package izumi.idealingua.model.typespace.structures

import izumi.idealingua.model.common.ExtendedField

import scala.collection.mutable

final case class FieldConflicts(
                                 goodFields: mutable.LinkedHashMap[String, ExtendedField]
                                 , softConflicts: mutable.LinkedHashMap[String, ExtendedField]
                                 , hardConflicts: mutable.LinkedHashMap[String, Seq[ExtendedField]]
                               ) {
  def good: List[ExtendedField] = goodFields.values.toList
  def soft: List[ExtendedField] = softConflicts.values.toList
  def all: List[ExtendedField] = good ++ soft
}

