package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.TypeSymb
import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait DependencyKeyProvider {
  def keyFromMethod(methodSymbol: TypeSymb): DIKey

  def keyFromParameter(parameterSymbol: TypeSymb): DIKey
}
