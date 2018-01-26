package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.TypeSymb

case class SelectedConstructor(constructorSymbol: TypeSymb, arguments: Seq[TypeSymb])
