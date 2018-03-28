package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.il.Struct

case class ScalaStruct(
                        unique: List[ScalaField]
                        , nonUnique: List[ScalaField]
                        , fields: Struct
                      ) {
  def all: List[ScalaField] = unique ++ nonUnique
}
