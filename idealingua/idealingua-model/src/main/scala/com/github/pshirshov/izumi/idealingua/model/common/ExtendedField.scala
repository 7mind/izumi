package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst

case class ExtendedField(field: ILAst.Field, definedBy: TypeId)
