package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil}

case class MacroSymbolInfoCompactImpl(
  name: String,
  finalResultType: scala.reflect.api.Universe#Type,
  friendlyAnnotations: List[FriendlyAnnotation],
  isByName: Boolean,
  wasGeneric: Boolean,
  safeFinalResultType: MacroSafeType,
) extends MacroSymbolInfoCompact {
  override final def withFriendlyAnnotations(annotations: List[FriendlyAnnotation]): MacroSymbolInfoCompact = copy(friendlyAnnotations = annotations)
}

object MacroSymbolInfoCompactImpl {

  def syntheticFromType(u: scala.reflect.api.Universe)(transformName: String => String)(tpe: u.Type): MacroSymbolInfoCompactImpl = {
    val annos = AnnotationTools.getAllTypeAnnotations(u)(tpe)
    val isByName = tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == u.definitions.ByNameParamClass
    val nonByNameFinalResultType = if (isByName) ReflectionUtil.stripByName(u: u.type)(tpe) else tpe
    val fst = MacroSafeType.create(u)(nonByNameFinalResultType)

    MacroSymbolInfoCompactImpl(
      name = transformName(tpe.typeSymbol.name.toString),
      finalResultType = tpe,
      friendlyAnnotations = annos.map(FriendlyAnnoTools.makeFriendly(u)),
      isByName = isByName,
      wasGeneric = tpe.typeSymbol.isParameter,
      safeFinalResultType = fst,
    )
  }

  def fromSymbol(u: scala.reflect.api.Universe)(underlying: u.Symbol): MacroSymbolInfoCompactImpl = {
    val isByName = (underlying.isTerm && underlying.asTerm.isByNameParam) || ReflectionUtil.isByName(u)(underlying.typeSignature)
    val annos = AnnotationTools.getAllAnnotations(u)(underlying).distinct

    val tpe = underlying.typeSignature
    val nonByNameFinalResultType = if (isByName) ReflectionUtil.stripByName(u: u.type)(tpe) else tpe
    val fst = MacroSafeType.create(u)(nonByNameFinalResultType)

    new MacroSymbolInfoCompactImpl(
      name = underlying.name.toTermName.toString,
      finalResultType = tpe,
      friendlyAnnotations = annos.map(FriendlyAnnoTools.makeFriendly(u)),
      isByName = isByName,
      wasGeneric = underlying.typeSignature.typeSymbol.isParameter,
      safeFinalResultType = fst,
    )
  }
}
