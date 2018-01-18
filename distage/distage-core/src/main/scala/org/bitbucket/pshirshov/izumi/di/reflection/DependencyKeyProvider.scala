package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}
import org.bitbucket.pshirshov.izumi.di.model.DIKey

trait DependencyKeyProvider {
  def keyFromMethod(methodSymbol: MethodSymb): DIKey

  def keyFromParameter(parameterSymbol: TypeSymb): DIKey

  def keyFromType(parameterSymbol: TypeFull): DIKey
}
