package izumi.distage.model.reflection.universe

import izumi.distage.model.exceptions.AnnotationConflictException
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil}

trait WithDISymbolInfo {
  this: DIUniverseBase
    with WithDISafeType =>

  sealed trait SymbolInfo {
    def name: String
    def finalResultType: SafeType

    def isByName: Boolean
    def wasGeneric: Boolean

    def annotations: List[u.Annotation]

    @deprecated("remove kludge", "0.9.0")
    def withTpe(tpe: SafeType): SymbolInfo
    // FIXME: by-names are broken ???
    @deprecated("remove kludge (by-names broken)", "0.9.0")
    def withIsByName(boolean: Boolean): SymbolInfo
    //def typeSignatureArgs: List[SymbolInfo] = underlying.typeSignature.typeArgs.map(_.typeSymbol).map(s => Runtime(s, definingClass))
  }

  protected def typeOfDistageAnnotation: TypeNative

  object SymbolInfo {

    /**
      * You can downcast from SymbolInfo if you need access to the underlying symbol reference (for example, to use a Mirror)
      */
    @deprecated("remove scala-reflect backed symbolinfo", "0.9.0")
    case class Runtime private (underlying: SymbNative, finalResultType: SafeType, isByName: Boolean, wasGeneric: Boolean, annotations: List[u.Annotation]) extends SymbolInfo {
      override final val name: String = underlying.name.toTermName.toString
      override final def withTpe(tpe: SafeType): SymbolInfo = copy(finalResultType = tpe)
      override final def withIsByName(boolean: Boolean): SymbolInfo = copy(isByName = boolean)
    }

    object Runtime {
      @deprecated("remove scala-reflect backed symbolinfo", "0.9.0")
      def apply(underlying: SymbNative, definingClass: SafeType, wasGeneric: Boolean, moreAnnotations: List[u.Annotation] = Nil): Runtime = {
        new Runtime(
          underlying = underlying,
          finalResultType = definingClass.use(tpe => SafeType(underlying.typeSignatureIn(tpe).finalResultType)),
          isByName = underlying.isTerm && underlying.asTerm.isByNameParam,
          wasGeneric = wasGeneric,
          annotations = (AnnotationTools.getAllAnnotations(u: u.type)(underlying) ++ moreAnnotations).distinct
        )
      }

      @deprecated("remove scala-reflect backed symbolinfo", "0.9.0")
      def apply(underlying: SymbNative): Runtime = {
        new Runtime(
          underlying = underlying,
          finalResultType = SafeType(underlying.typeSignature),
          isByName = (underlying.isTerm && underlying.asTerm.isByNameParam) || ReflectionUtil.isByName(u)(underlying.typeSignature),
          wasGeneric = underlying.typeSignature.typeSymbol.isParameter,
          annotations = (AnnotationTools.getAllAnnotations(u: u.type)(underlying)).distinct
        )
      }
    }

    case class Static(
                       name: String,
                       finalResultType: SafeType,
                       annotations: List[u.Annotation],
                       isByName: Boolean,
                       wasGeneric: Boolean,
                     ) extends SymbolInfo {
      override final def withTpe(tpe: SafeType): SymbolInfo = copy(finalResultType = tpe)
      override final def withIsByName(boolean: Boolean): SymbolInfo = copy(isByName = boolean)
    }

    implicit final class SymbolInfoExtensions(symbolInfo: SymbolInfo) {
      def findUniqueAnnotation(annType: TypeNative): Option[u.Annotation] = {
        val distageAnnos = symbolInfo.annotations.filter(t => t.tree.tpe <:< typeOfDistageAnnotation).toSet

        if (distageAnnos.size > 1) {
          import izumi.fundamentals.platform.strings.IzString._
          throw new AnnotationConflictException(s"Multiple DI annotations on symbol `$symbolInfo` in ${symbolInfo.finalResultType}: ${distageAnnos.niceList()}")
        }

        findAnnotation(annType)
      }

      private[this] def findAnnotation(tgtAnnType: TypeNative): Option[u.Annotation] = {
        symbolInfo.annotations.find(a => AnnotationTools.annotationTypeEq(u)(tgtAnnType, a))
      }
    }

  }

}
