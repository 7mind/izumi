package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.model.plan.DependencyContext
import com.github.pshirshov.izumi.fundamentals.reflection._
import com.github.pshirshov.izumi.fundamentals.reflection.{AnnotationTools, EqualitySafeType}

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
    AnnotationTools.find[Id](parameterSymbol)
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

