package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.definition.Id
import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}


import scala.reflect.runtime.universe

class DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  // TODO: named dependencies

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: TypeSymb): DIKey = {
    val typeKey = DIKey.TypeKey(EqualitySafeType(parameterSymbol.typeSignature))

    withOptionalName(parameterSymbol, typeKey)
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: MethodSymb): DIKey = {
    val typeKey = DIKey.TypeKey(EqualitySafeType(methodSymbol.returnType))
    withOptionalName(methodSymbol, typeKey)

  }

  private def withOptionalName(parameterSymbol: TypeSymb, typeKey: DIKey.TypeKey) = {
    parameterSymbol
      .annotations
      .find(_.tree.tpe =:= universe.typeOf[Id])
      .flatMap {
        ann =>
          ann.tree.children.tail
            .collect {
              case l: universe.LiteralApi =>
                l.value
            }
            .collectFirst {
              case universe.Constant(name: String) =>
                name
            }
      } match {
      case Some(ann) =>
        typeKey.named(ann)

      case _ =>
        typeKey
    }
  }

  override def keyFromType(parameterType: TypeFull): DIKey = {
    DIKey.TypeKey(parameterType)
  }
}
