package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition

case class Anno(name: String, values: Map[String, ConstValue], position: InputPosition)
