package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed

case class ExtendedField(field: typed.Field, definedBy: TypeId)
