package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition

case class RawAnno(name: String, values: RawVal.CMap, position: InputPosition)
