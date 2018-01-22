package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}

class DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  // TODO: named dependencies

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: TypeSymb): DIKey = {
    DIKey.TypeKey(EqualitySafeType(parameterSymbol.typeSignature))
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: MethodSymb): DIKey = {
    DIKey.TypeKey(EqualitySafeType(methodSymbol.returnType))
  }

  override def keyFromType(parameterType: TypeFull): DIKey = {
    DIKey.TypeKey(parameterType)
  }
}
