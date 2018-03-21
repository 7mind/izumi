package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.Fields

case class ScalaFields(
                        unique: List[ScalaField]
                        , nonUnique: List[ScalaField]
                        , fields: Fields
                      ) {
  def all: List[ScalaField] = unique ++ nonUnique
}
