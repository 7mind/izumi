package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

trait WithDISymbolInfo {
  this: DIUniverseBase
    with WithDISafeType =>

  trait SymbolInfo {
    def name: String
    def finalResultType: SafeType
    def annotations: List[u.Annotation]
    def definingClass: SafeType

    override def toString: String = name
  }

  object SymbolInfo {
    /**
    * You can downcast from SymbolInfo if you need access to the underlying symbol reference (for example, to use a Mirror)
    **/
    case class Runtime(underlying: Symb, definingClass: SafeType) extends SymbolInfo {
      override val name: String = underlying.name.toTermName.toString

      override val finalResultType: SafeType = SafeType(underlying.typeSignatureIn(definingClass.tpe).finalResultType)

      override val annotations: List[u.Annotation] = AnnotationTools.getAllAnnotations(u: u.type)(underlying)
    }

    case class Static(name: String, finalResultType: SafeType, annotations: List[u.Annotation], definingClass: SafeType) extends SymbolInfo

    def apply(symb: Symb, definingClass: SafeType): SymbolInfo =
      Runtime(symb, definingClass)

    implicit final class SymbolInfoExtensions(symbolInfo: SymbolInfo) {
      def findAnnotation(annType: SafeType): Option[u.Annotation] =
        symbolInfo.annotations.find(AnnotationTools.annotationTypeEq(u)(annType.tpe, _))
    }
  }
}
