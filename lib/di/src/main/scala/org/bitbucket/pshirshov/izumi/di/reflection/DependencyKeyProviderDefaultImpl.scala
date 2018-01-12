package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.TypeSymb
import org.bitbucket.pshirshov.izumi.di.model.DIKey

class DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {

  override def keyFromMethod(methodSymbol: TypeSymb): DIKey = DIKey.TypeKey(methodSymbol.info.resultType)

  override def keyFromParameter(parameterSymbol: TypeSymb): DIKey = DIKey.TypeKey(parameterSymbol.info)
}
