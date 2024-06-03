package izumi.distage.reflection.macros.universe.impl

import izumi.distage.model.exceptions.macros.reflection.AnnotationConflictException
import izumi.distage.reflection.macros.universe.basicuniverse.{FriendlyAnnotation, MacroSafeType, MacroSymbolInfoCompact}
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil}

trait WithDISymbolInfo { this: DIUniverseBase =>

  sealed trait MacroSymbolInfo extends MacroSymbolInfoCompact {
    def name: String
    def finalResultType: TypeNative
    final def nonByNameFinalResultType: TypeNative = {
      if (isByName) ReflectionUtil.stripByName(u: u.type)(finalResultType) else finalResultType
    }

    def isByName: Boolean
    def wasGeneric: Boolean

    def annotations: List[u.Annotation]
    def friendlyAnnotations: List[FriendlyAnnotation]

    def withTpe(tpe: TypeNative): MacroSymbolInfo
    def withIsByName(boolean: Boolean): MacroSymbolInfo
    def withAnnotations(annotations: List[u.Annotation]): MacroSymbolInfo
  }

  protected def typeOfDistageAnnotation: TypeNative

  object MacroSymbolInfo {

    /**
      * You can downcast from SymbolInfo if you need access to the underlying symbol reference (for example, to use a Mirror)
      */
    private[universe] case class Runtime private (
      underlying: SymbNative,
      typeSignatureInDefiningClass: TypeNative,
      finalResultType: TypeNative,
      isByName: Boolean,
      wasGeneric: Boolean,
      annotations: List[u.Annotation],
      friendlyAnnotations: List[FriendlyAnnotation],
    ) extends MacroSymbolInfo {
      override final val name: String = underlying.name.toTermName.toString
      override final def withTpe(tpe: TypeNative): MacroSymbolInfo = copy(finalResultType = tpe)
      override final def withIsByName(boolean: Boolean): MacroSymbolInfo = copy(isByName = boolean)
      override final def withAnnotations(annotations: List[u.Annotation]): MacroSymbolInfo = copy(annotations = annotations)
      override final def withFriendlyAnnotations(annotations: List[FriendlyAnnotation]): MacroSymbolInfoCompact = copy(friendlyAnnotations = annotations)
      override final def safeFinalResultType: MacroSafeType = MacroSafeType.create(ctx.universe)(nonByNameFinalResultType.asInstanceOf[ctx.Type])
    }

    private[distage] object Runtime {
      def apply(
        underlying: SymbNative,
        definingClass: TypeNative,
        wasGeneric: Boolean,
        moreAnnotations: List[u.Annotation] = Nil,
      ): Runtime = {
        val tpeIn = underlying
          .typeSignatureIn(definingClass)
          .asSeenFrom(definingClass, definingClass.typeSymbol)
        val annos = (AnnotationTools.getAllAnnotations(u: u.type)(underlying) ++ moreAnnotations).distinct
        new Runtime(
          underlying = underlying,
          typeSignatureInDefiningClass = tpeIn,
          finalResultType = tpeIn.finalResultType,
          isByName = underlying.isTerm && underlying.asTerm.isByNameParam,
          wasGeneric = wasGeneric,
          annotations = annos,
          friendlyAnnotations = annos.map(FriendlyAnnoTools.makeFriendly(u)),
        )
      }

      def apply(underlying: SymbNative): Runtime = {
        val annos = AnnotationTools.getAllAnnotations(u: u.type)(underlying).distinct
        new Runtime(
          underlying = underlying,
          typeSignatureInDefiningClass = underlying.typeSignature,
          finalResultType = underlying.typeSignature,
          isByName = (underlying.isTerm && underlying.asTerm.isByNameParam) || ReflectionUtil.isByName(u)(underlying.typeSignature),
          wasGeneric = underlying.typeSignature.typeSymbol.isParameter,
          annotations = annos,
          friendlyAnnotations = annos.map(FriendlyAnnoTools.makeFriendly(u)),
        )
      }
    }

    case class Static(
      name: String,
      finalResultType: TypeNative,
      annotations: List[u.Annotation],
      friendlyAnnotations: List[FriendlyAnnotation],
      isByName: Boolean,
      wasGeneric: Boolean,
    ) extends MacroSymbolInfo {
      override final def withTpe(tpe: TypeNative): MacroSymbolInfo = copy(finalResultType = tpe)
      override final def withIsByName(boolean: Boolean): MacroSymbolInfo = copy(isByName = boolean)
      override final def withAnnotations(annotations: List[u.Annotation]): MacroSymbolInfo = copy(annotations = annotations)
      override final def withFriendlyAnnotations(annotations: List[FriendlyAnnotation]): MacroSymbolInfoCompact = copy(friendlyAnnotations = annotations)
      override final def safeFinalResultType: MacroSafeType = MacroSafeType.create(ctx.universe)(nonByNameFinalResultType.asInstanceOf[ctx.Type])
    }
    object Static {
      def syntheticFromType(transformName: String => String)(tpe: TypeNative): MacroSymbolInfo.Static = {
        val annos = AnnotationTools.getAllTypeAnnotations(u)(tpe)
        MacroSymbolInfo.Static(
          name = transformName(tpe.typeSymbol.name.toString),
          finalResultType = tpe,
          annotations = annos,
          friendlyAnnotations = annos.map(FriendlyAnnoTools.makeFriendly(u)),
          isByName = tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == u.definitions.ByNameParamClass,
          wasGeneric = tpe.typeSymbol.isParameter,
        )
      }
    }

    implicit final class SymbolInfoExtensions(symbolInfo: MacroSymbolInfo) {
      def findUniqueAnnotation(annType: TypeNative): Option[u.Annotation] = {
        val distageAnnos = symbolInfo.annotations.filter(t => t.tree.tpe <:< typeOfDistageAnnotation).toSet

        if (distageAnnos.size > 1) {
          import izumi.fundamentals.platform.strings.IzString.*
          throw new AnnotationConflictException(s"Multiple DI annotations on symbol `$symbolInfo` in ${symbolInfo.finalResultType}: ${distageAnnos.niceList()}")
        }

        findAnnotation(annType)
      }

      private[this] def findAnnotation(tgtAnnType: TypeNative): Option[u.Annotation] = {
        val r = symbolInfo.annotations.find(a => AnnotationTools.annotationTypeEq(u)(tgtAnnType, a))
        r
      }
    }

  }

}



