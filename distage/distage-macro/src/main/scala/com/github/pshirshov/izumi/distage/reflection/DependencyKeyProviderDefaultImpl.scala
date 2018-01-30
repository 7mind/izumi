package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.reflection.DependencyKeyProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MacroUniverse, RuntimeUniverse}
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

trait DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  import u._
  import u.u._

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: Symb): DIKey = {
    val typeKey = DIKey.TypeKey(SafeType(parameterSymbol.typeSignatureIn(context.definingClass.tpe)))

    withOptionalName(parameterSymbol, typeKey)
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: MethodSymb): DIKey = {
    val typeKey = DIKey.TypeKey(SafeType(methodSymbol.typeSignatureIn(context.definingClass.tpe).finalResultType))
    withOptionalName(methodSymbol, typeKey)
  }

  private def withOptionalName(parameterSymbol: Symb, typeKey: DIKey.TypeKey) = {
    AnnotationTools.find[Id](u.u: u.u.type)(parameterSymbol)
      .flatMap {
        ann =>
          ann.tree.children.tail
            .collect {
              case l: LiteralApi =>
                l.value
            }
            .collectFirst {
              case Constant(name: String) =>
                name
            }
      } match {
      case Some(ann) =>
        typeKey.named(ann)

      case _ =>
        typeKey
    }
  }

}

object DependencyKeyProviderDefaultImpl {

  class Java
    extends DependencyKeyProvider.Java
       with DependencyKeyProviderDefaultImpl {
    override val u: RuntimeUniverse.type = RuntimeUniverse
  }
  object Java {
    final val instance = new DependencyKeyProviderDefaultImpl.Java
  }

  class Macro[M <: MacroUniverse[_]](macroUniverse: M)
    extends DependencyKeyProvider.Macro[M](macroUniverse)
       with DependencyKeyProviderDefaultImpl
  object Macro {
    def instance[M <: MacroUniverse[_]](macroUniverse: M): Macro[macroUniverse.type] =
      new Macro(macroUniverse)
  }
}

