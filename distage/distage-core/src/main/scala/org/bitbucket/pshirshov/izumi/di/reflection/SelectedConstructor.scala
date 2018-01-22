package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.TypeSymb

case class SelectedConstructor(constructorSymbol: TypeSymb, arguments: Seq[TypeSymb])
