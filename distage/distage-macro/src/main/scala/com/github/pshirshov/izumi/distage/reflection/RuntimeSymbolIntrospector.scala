package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.fundamentals.reflection.RuntimeUniverse


trait RuntimeSymbolIntrospector
  extends AbstractSymbolIntrospector
    with SymbolIntrospectorAbstractImpl {
  override val u: RuntimeUniverse.type = RuntimeUniverse

}
