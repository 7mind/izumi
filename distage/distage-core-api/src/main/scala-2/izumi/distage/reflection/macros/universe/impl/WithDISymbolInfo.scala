package izumi.distage.reflection.macros.universe.impl

import izumi.distage.model.exceptions.macros.reflection.AnnotationConflictException
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil}

trait WithDISymbolInfo { this: DIUniverseBase with WithDISafeType =>

  sealed trait FriendlyAnnoParams
  object FriendlyAnnoParams {
    case class Full(values: List[(String, FriendlyAnnotationValue)]) extends FriendlyAnnoParams
    case class Values(values: List[FriendlyAnnotationValue]) extends FriendlyAnnoParams

  }
  case class FriendlyAnnotation(fqn: String, params: FriendlyAnnoParams)
  sealed trait FriendlyAnnotationValue
  object FriendlyAnnotation {
    case class StringValue(value: String) extends FriendlyAnnotationValue
    case class IntValue(value: Int) extends FriendlyAnnotationValue
    case class LongValue(value: Long) extends FriendlyAnnotationValue
    case class UnknownConst(value: Any) extends FriendlyAnnotationValue
    case class Unknown() extends FriendlyAnnotationValue
  }

  def makeFriendly(anno: u.Annotation): FriendlyAnnotation = {
    import u.*

    val tpe = anno.tree.tpe.finalResultType
//    val tag = LightTypeTagImpl.makeLightTypeTag(u)(tpe)
//    println(tag)
    val annoName = tpe.typeSymbol.fullName
    val paramTrees = anno.tree.children.tail
    val values = paramTrees.map {
      p =>
        (p: @unchecked) match {
          case Literal(Constant(c)) =>
            c match {
              case v: String =>
                FriendlyAnnotation.StringValue(v)
              case v: Int =>
                FriendlyAnnotation.IntValue(v)
              case v: Long =>
                FriendlyAnnotation.LongValue(v)
              case v =>
                FriendlyAnnotation.UnknownConst(v)
            }
          case _ =>
            FriendlyAnnotation.Unknown()
        }
    }

    val constructor = rp.selectConstructorMethod(tpe.asInstanceOf[rp.u.TypeNative])

//    println((anno, u.showRaw(anno.tree.children.head)))
    println(tpe)
    val avals = constructor match {
      case Some(c) =>
        c.paramLists match {
          case params :: Nil =>
            val names = params.map(_.name.decodedName.toString)
            assert(names.size == values.size)
            FriendlyAnnoParams.Full(names.zip(values))
          case _ =>
            FriendlyAnnoParams.Values(values)
        }
      case _ =>
        FriendlyAnnoParams.Values(values)
    }

    FriendlyAnnotation(annoName, avals)
  }

  sealed trait MacroSymbolInfo {
    def name: String
    def finalResultType: TypeNative
    final def nonByNameFinalResultType: TypeNative = if (isByName) ReflectionUtil.stripByName(u: u.type)(finalResultType) else finalResultType

    def isByName: Boolean
    def wasGeneric: Boolean

    def annotations: List[u.Annotation]

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
    ) extends MacroSymbolInfo {
      override final val name: String = underlying.name.toTermName.toString
      override final def withTpe(tpe: TypeNative): MacroSymbolInfo = copy(finalResultType = tpe)
      override final def withIsByName(boolean: Boolean): MacroSymbolInfo = copy(isByName = boolean)
      override final def withAnnotations(annotations: List[u.Annotation]): MacroSymbolInfo = copy(annotations = annotations)
    }

    private[distage] object Runtime {
      def apply(underlying: SymbNative, definingClass: TypeNative, wasGeneric: Boolean, moreAnnotations: List[u.Annotation] = Nil): Runtime = {
        val tpeIn = underlying
          .typeSignatureIn(definingClass)
          .asSeenFrom(definingClass, definingClass.typeSymbol)
        new Runtime(
          underlying = underlying,
          typeSignatureInDefiningClass = tpeIn,
          finalResultType = tpeIn.finalResultType,
          isByName = underlying.isTerm && underlying.asTerm.isByNameParam,
          wasGeneric = wasGeneric,
          annotations = (AnnotationTools.getAllAnnotations(u: u.type)(underlying) ++ moreAnnotations).distinct,
        )
      }

      def apply(underlying: SymbNative): Runtime = {
        new Runtime(
          underlying = underlying,
          typeSignatureInDefiningClass = underlying.typeSignature,
          finalResultType = underlying.typeSignature,
          isByName = (underlying.isTerm && underlying.asTerm.isByNameParam) || ReflectionUtil.isByName(u)(underlying.typeSignature),
          wasGeneric = underlying.typeSignature.typeSymbol.isParameter,
          annotations = AnnotationTools.getAllAnnotations(u: u.type)(underlying).distinct,
        )
      }
    }

    case class Static(
      name: String,
      finalResultType: TypeNative,
      annotations: List[u.Annotation],
      isByName: Boolean,
      wasGeneric: Boolean,
    ) extends MacroSymbolInfo {
      override final def withTpe(tpe: TypeNative): MacroSymbolInfo = copy(finalResultType = tpe)
      override final def withIsByName(boolean: Boolean): MacroSymbolInfo = copy(isByName = boolean)
      override final def withAnnotations(annotations: List[u.Annotation]): MacroSymbolInfo = copy(annotations = annotations)
    }
    object Static {
      def syntheticFromType(transformName: String => String)(tpe: TypeNative): MacroSymbolInfo.Static = {
        MacroSymbolInfo.Static(
          name = transformName(tpe.typeSymbol.name.toString),
          finalResultType = tpe,
          annotations = AnnotationTools.getAllTypeAnnotations(u)(tpe),
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
        r.foreach {
          a =>
            println(makeFriendly(a))
        }
        r
      }
    }

  }

}
