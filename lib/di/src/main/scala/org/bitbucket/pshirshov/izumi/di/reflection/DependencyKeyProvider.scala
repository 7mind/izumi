package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait DependencyKeyProvider {
  def keyFromMethod(methodSymbol: Symb): DIKey

  def keyFromParameter(parameterSymbol: Symb): DIKey
}
