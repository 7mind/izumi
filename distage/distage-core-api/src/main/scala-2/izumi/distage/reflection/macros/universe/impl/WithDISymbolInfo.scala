package izumi.distage.reflection.macros.universe.impl

import izumi.distage.model.exceptions.macros.reflection.AnnotationConflictException
import izumi.distage.reflection.macros.universe.basicuniverse
import izumi.distage.reflection.macros.universe.basicuniverse.{FriendlyAnnoParams, FriendlyAnnotation, FriendlyAnnotationValue, MacroSafeType, MacroSymbolInfoCompact}
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil}

trait WithDISymbolInfo { this: DIUniverseBase =>

  private def convertConst(c: Any) = {
    c match {
      case v: String =>
        FriendlyAnnotationValue.StringValue(v)
      case v: Int =>
        FriendlyAnnotationValue.IntValue(v)
      case v: Long =>
        FriendlyAnnotationValue.LongValue(v)
      case v =>
        FriendlyAnnotationValue.UnknownConst(v)
    }
  }
  def makeFriendly(anno: u.Annotation): FriendlyAnnotation = {
    import u.*

    val tpe = anno.tree.tpe.finalResultType
    val annoName = tpe.typeSymbol.fullName
    val paramTrees = anno.tree.children.tail

    val avals = if (tpe.typeSymbol.isJavaAnnotation) {
      val pairs = paramTrees.map {
        p =>
          (p: @unchecked) match {
            case NamedArg(Ident(TermName(name)), Literal(Constant(c))) =>
              (Some(name), convertConst(c))
            case a =>
              (None, FriendlyAnnotationValue.UnknownConst(s"$a ${u.showRaw(a)}"))
          }
      }

      val names = pairs.map(_._1).collect { case Some(name) => name }
      val values = pairs.map(_._2)
      assert(names.size >= values.size, s"Java annotation structure disbalance: names=$names values=$values")
      FriendlyAnnoParams.Full(names.zip(values ++ List.fill(names.size - values.size)(FriendlyAnnotationValue.UnsetValue())))
    } else {
      val values = paramTrees.map {
        p =>
          (p: @unchecked) match {
            case Literal(Constant(c)) =>
              convertConst(c)
            case a =>
              FriendlyAnnotationValue.UnknownConst(s"$a ${u.showRaw(a)}")
          }
      }
      val constructor = rp.selectConstructorMethod(tpe.asInstanceOf[rp.u.TypeNative])
      constructor match {
        case Some(c) =>
          c.paramLists match {
            case params :: Nil =>
              val names = params.map(_.name.decodedName.toString)
              assert(names.size >= values.size, s"Annotation structure disbalance: names=$names values=$values")
              FriendlyAnnoParams.Full(names.zip(values ++ List.fill(names.size - values.size)(FriendlyAnnotationValue.UnsetValue())))
            case _ =>
              FriendlyAnnoParams.Values(values)
          }

        case _ =>
          FriendlyAnnoParams.Values(values)
      }
    }

    basicuniverse.FriendlyAnnotation(annoName, avals)
  }

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
    // def typeSignatureArgs: List[SymbolInfo] = underlying.typeSignature.typeArgs.map(_.typeSymbol).map(s => Runtime(s, definingClass))
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
      override final def safeFinalResultType: MacroSafeType = MacroSafeType.create(ctx)(nonByNameFinalResultType.asInstanceOf[ctx.Type])
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
          friendlyAnnotations = annos.map(makeFriendly),
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
          friendlyAnnotations = annos.map(makeFriendly),
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
      override final def safeFinalResultType: MacroSafeType = MacroSafeType.create(ctx)(nonByNameFinalResultType.asInstanceOf[ctx.Type])
    }
    object Static {
      def syntheticFromType(transformName: String => String)(tpe: TypeNative): MacroSymbolInfo.Static = {
        val annos = AnnotationTools.getAllTypeAnnotations(u)(tpe)
        MacroSymbolInfo.Static(
          name = transformName(tpe.typeSymbol.name.toString),
          finalResultType = tpe,
          annotations = annos,
          friendlyAnnotations = annos.map(makeFriendly),
          isByName = tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == u.definitions.ByNameParamClass,
          wasGeneric = tpe.typeSymbol.isParameter,
        )
      }
    }

    implicit final class SymbolInfoExtensions(symbolInfo: MacroSymbolInfo) {
      def findUniqueFriendlyAnno(p: FriendlyAnnotation => Boolean): Option[FriendlyAnnotation] = {
        val annos = symbolInfo.friendlyAnnotations.filter(p)
        if (annos.size > 1) {
          import izumi.fundamentals.platform.strings.IzString.*
          throw new AnnotationConflictException(s"Multiple DI annotations on symbol `$symbolInfo` in ${symbolInfo.finalResultType}: ${annos.niceList()}")
        }
        annos.headOption
      }

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
