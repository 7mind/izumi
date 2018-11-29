package com.github.pshirshov.izumi.idealingua.model.parser

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawAdtMember

final case class AlgebraicType(alternatives: List[RawAdtMember])
