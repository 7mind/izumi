package org.bitbucket.pshirshov.izumi.distage.reflection

import org.bitbucket.pshirshov.izumi.distage.model.DIKey
import org.bitbucket.pshirshov.izumi.distage.reflection.DependencyContext.{MethodContext, ParameterContext}
import org.bitbucket.pshirshov.izumi.distage.{MethodSymb, TypeFull, TypeSymb}


trait DependencyKeyProvider {
  def keyFromParameter(context: ParameterContext, parameterSymbol: TypeSymb): DIKey

  def keyFromMethod(context: MethodContext, methodSymbol: MethodSymb): DIKey

  def keyFromType(parameterSymbol: TypeFull): DIKey
}
