package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.exceptions.AnnotationConflictException
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

  protected def typeOfDistageAnnotation: SafeType

  object SymbolInfo {
    /**
    * You can downcast from SymbolInfo if you need access to the underlying symbol reference (for example, to use a Mirror)
    **/
    case class Runtime(underlying: Symb, definingClass: SafeType, annotations: List[u.Annotation]) extends SymbolInfo {
      override val name: String = underlying.name.toTermName.toString

      override val finalResultType: SafeType = SafeType(underlying.typeSignatureIn(definingClass.tpe).finalResultType)
    }

    object Runtime {
      def apply(underlying: Symb, definingClass: SafeType, annotations: List[u.Annotation]): Runtime =
        new Runtime(underlying, definingClass, AnnotationTools.getAllAnnotations(u: u.type)(underlying) ++ annotations)

      def apply(underlying: Symb, definingClass: SafeType): Runtime =
        new Runtime(underlying, definingClass, AnnotationTools.getAllAnnotations(u: u.type)(underlying))
    }

    case class Static(name: String, finalResultType: SafeType, annotations: List[u.Annotation], definingClass: SafeType) extends SymbolInfo

    def apply(symb: Symb, definingClass: SafeType): SymbolInfo =
      Runtime(symb, definingClass)

    implicit final class SymbolInfoExtensions(symbolInfo: SymbolInfo) {
      def findAnnotation(annType: SafeType): Option[u.Annotation] = {
        symbolInfo.annotations.find(AnnotationTools.annotationTypeEq(u)(annType.tpe, _))
      }

      def findUniqueAnnotation(annType: SafeType): Option[u.Annotation] = {
        val distageAnnos = symbolInfo.annotations.filter(t => SafeType(t.tree.tpe) <:< typeOfDistageAnnotation).toSet

        if (distageAnnos.size > 1) {
          import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
          throw new AnnotationConflictException(s"Multiple DI annotations on symbol `$symbolInfo` in ${symbolInfo.definingClass}: ${distageAnnos.niceList()}")
        }

        symbolInfo.findAnnotation(annType)
      }
    }
  }
}
