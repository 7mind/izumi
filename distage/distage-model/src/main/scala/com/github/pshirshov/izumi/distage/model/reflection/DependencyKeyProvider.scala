package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.distage.model.plan.DependencyContext.{MethodContext, ParameterContext}
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.fundamentals.reflection._


trait DependencyKeyProvider {
  def keyFromParameter(context: ParameterContext, parameterSymbol: RuntimeUniverse.TypeSymb): DIKey

  def keyFromMethod(context: MethodContext, methodSymbol: RuntimeUniverse.MethodSymb): DIKey

  def keyFromType(parameterSymbol: RuntimeUniverse.TypeFull): DIKey
}
