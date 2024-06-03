package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.distage.reflection.macros.universe.basicuniverse.exceptions.AnnotationConflictException

trait MacroSymbolInfoCompact {
  def name: String
  def isByName: Boolean
  def wasGeneric: Boolean
  def friendlyAnnotations: List[FriendlyAnnotation]
  def withFriendlyAnnotations(annotations: List[FriendlyAnnotation]): MacroSymbolInfoCompact
  def safeFinalResultType: MacroSafeType
  def finalResultType: scala.reflect.api.Universe#Type
}

object MacroSymbolInfoCompact {
  implicit final class CompactSymbolInfoExtensions(symbolInfo: MacroSymbolInfoCompact) {
    def findUniqueFriendlyAnno(p: FriendlyAnnotation => Boolean): Option[FriendlyAnnotation] = {
      val annos = symbolInfo.friendlyAnnotations.filter(p)
      if (annos.size > 1) {
        import izumi.fundamentals.platform.strings.IzString.*
        throw new AnnotationConflictException(s"Multiple DI annotations on symbol `$symbolInfo` in ${symbolInfo.safeFinalResultType}: ${annos.niceList()}")
      }
      annos.headOption
    }
  }
}


