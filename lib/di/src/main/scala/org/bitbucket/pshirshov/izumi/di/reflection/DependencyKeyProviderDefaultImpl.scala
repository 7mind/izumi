package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.DIKey

class DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  override def keyFromMethod(methodSymbol: Symb): DIKey = DIKey.TypeKey(methodSymbol.info.resultType.typeSymbol)

  override def keyFromParameter(parameterSymbol: Symb): DIKey = DIKey.TypeKey(parameterSymbol.info.typeSymbol)
}
