package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.exceptions.AnnotationConflictException
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

trait WithDISymbolInfo {
  this: DIUniverseBase
    with WithDISafeType =>

  trait SymbolInfo {
    def name: String

    def finalResultType: SafeType

    def definingClass: SafeType

    def isByName: Boolean

    def wasGeneric: Boolean

    def annotations: List[u.Annotation]

    //def typeSignatureArgs: List[SymbolInfo]

    override def toString: String = name
  }

  protected def typeOfDistageAnnotation: SafeType

  object SymbolInfo {

    /**
      * You can downcast from SymbolInfo if you need access to the underlying symbol reference (for example, to use a Mirror)
      **/
    case class Runtime private (underlying: Symb, definingClass: SafeType, wasGeneric: Boolean, annotations: List[u.Annotation]) extends SymbolInfo {
      override val name: String = underlying.name.toTermName.toString

      override val finalResultType: SafeType = SafeType(underlying.typeSignatureIn(definingClass.tpe).finalResultType)

      override def isByName: Boolean = underlying.isTerm && underlying.asTerm.isByNameParam

      //override def typeSignatureArgs: List[SymbolInfo] = underlying.typeSignature.typeArgs.map(_.typeSymbol).map(s => Runtime(s, definingClass))
    }

    object Runtime {
      def apply(underlying: Symb, definingClass: SafeType, wasGeneric: Boolean, moreAnnotations: List[u.Annotation]): Runtime = {
        new Runtime(underlying, definingClass, wasGeneric, (AnnotationTools.getAllAnnotations(u: u.type)(underlying) ++ moreAnnotations).distinct)
      }

      def apply(underlying: Symb, definingClass: SafeType, wasGeneric: Boolean): Runtime = {
        new Runtime(underlying, definingClass, wasGeneric, AnnotationTools.getAllAnnotations(u: u.type)(underlying).distinct)
      }
    }

    case class Static(
                       name: String
                       , finalResultType: SafeType
                       , annotations: List[u.Annotation]
                       , definingClass: SafeType
                       , isByName: Boolean
                       , wasGeneric: Boolean
                     ) extends SymbolInfo

    def apply(symb: Symb, definingClass: SafeType, wasGeneric: Boolean): SymbolInfo = Runtime(symb, definingClass, wasGeneric)

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
