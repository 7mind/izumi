package org.bitbucket.pshirshov.izumi.distage.reflection

import org.bitbucket.pshirshov.izumi.distage.TypeSymb

case class SelectedConstructor(constructorSymbol: TypeSymb, arguments: Seq[TypeSymb])
