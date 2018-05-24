package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

trait WithDISymbolInfo {
  this: DIUniverseBase
    with WithDISafeType =>

  trait SymbolInfo {
    def name: String
    def finalResultType: TypeFull
    def annotations: List[u.Annotation]
    def isMethodSymbol: Boolean
    def definingClass: TypeFull

    override def toString: String = name
  }

  object SymbolInfo {
    /**
    * You can downcast from SymbolInfo if you need access to the underlying symbol reference (for example, to use a Mirror)
    **/
    case class RuntimeSymbol(underlying: Symb, definingClass: TypeFull) extends SymbolInfo {
      override val name: String = underlying.name.toTermName.toString

      override val finalResultType: TypeFull = SafeType(underlying.typeSignatureIn(definingClass.tpe).finalResultType)

      override val annotations: List[u.Annotation] = AnnotationTools.getAllAnnotations(u: u.type)(underlying)

      override val isMethodSymbol: Boolean = underlying.isMethod
    }

    case class StaticSymbol(name: String, finalResultType: TypeFull, annotations: List[u.Annotation], isMethodSymbol: Boolean, definingClass: TypeFull) extends SymbolInfo

    def apply(symb: Symb, definingClass: TypeFull): SymbolInfo =
      RuntimeSymbol(symb, definingClass)

    implicit final class SymbolInfoExtensions(symbolInfo: SymbolInfo) {
      def findAnnotation(annType: TypeFull): Option[u.Annotation] =
        symbolInfo.annotations.find(AnnotationTools.annotationTypeEq(u)(annType.tpe, _))
    }
  }
}
