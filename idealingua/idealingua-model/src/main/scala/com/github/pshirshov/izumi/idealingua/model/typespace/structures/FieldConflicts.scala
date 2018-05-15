package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.ExtendedField
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field

final case class FieldConflicts (
                                   all: Seq[ExtendedField]
                                   , goodFields: Map[String, Seq[ExtendedField]]
                                   , softConflicts: Map[String, Map[Field, Seq[ExtendedField]]]
                                   , hardConflicts: Map[String, Map[Field, Seq[ExtendedField]]]
                                 )

