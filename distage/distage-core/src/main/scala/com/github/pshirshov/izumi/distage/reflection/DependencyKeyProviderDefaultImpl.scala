package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.plan.DependencyContext
import com.github.pshirshov.izumi.distage.model.references.DIKey
import com.github.pshirshov.izumi.distage.model.reflection.DependencyKeyProvider
import com.github.pshirshov.izumi.fundamentals.reflection.{AnnotationTools, _}

class DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  // TODO: named dependencies

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: RuntimeUniverse.TypeSymb): DIKey = {
    val typeKey = DIKey.TypeKey(RuntimeUniverse.SafeType(parameterSymbol.typeSignature))

    withOptionalName(parameterSymbol, typeKey)
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: RuntimeUniverse.MethodSymb): DIKey = {
    val typeKey = DIKey.TypeKey(RuntimeUniverse.SafeType(methodSymbol.typeSignatureIn(context.definingClass.tpe).finalResultType))
    withOptionalName(methodSymbol, typeKey)

  }

  private def withOptionalName(parameterSymbol: RuntimeUniverse.TypeSymb, typeKey: DIKey.TypeKey) = {
    AnnotationTools.find[Id](parameterSymbol)
      .flatMap {
        ann =>
          ann.tree.children.tail
            .collect {
              case l: RuntimeUniverse.u.LiteralApi =>
                l.value
            }
            .collectFirst {
              case RuntimeUniverse.u.Constant(name: String) =>
                name
            }
      } match {
      case Some(ann) =>
        typeKey.named(ann)

      case _ =>
        typeKey
    }
  }

  override def keyFromType(parameterType: RuntimeUniverse.TypeFull): DIKey = {
    DIKey.TypeKey(parameterType)
  }
}

