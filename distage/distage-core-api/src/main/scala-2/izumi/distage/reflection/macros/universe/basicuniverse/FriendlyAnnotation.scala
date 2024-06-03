package izumi.distage.reflection.macros.universe.basicuniverse

import izumi.distage.model.exceptions.macros.reflection.AnnotationConflictException

case class FriendlyAnnotation(fqn: String, params: FriendlyAnnoParams)

sealed trait FriendlyAnnoParams {
  def values: List[FriendlyAnnotationValue]
}
object FriendlyAnnoParams {
  case class Full(named: List[(String, FriendlyAnnotationValue)]) extends FriendlyAnnoParams {
    override def values: List[FriendlyAnnotationValue] = named.map(_._2)
  }
  case class Values(values: List[FriendlyAnnotationValue]) extends FriendlyAnnoParams

}

sealed trait FriendlyAnnotationValue
object FriendlyAnnotationValue {
  case class StringValue(value: String) extends FriendlyAnnotationValue
  case class IntValue(value: Int) extends FriendlyAnnotationValue
  case class LongValue(value: Long) extends FriendlyAnnotationValue
  case class UnsetValue() extends FriendlyAnnotationValue
  case class UnknownConst(value: Any) extends FriendlyAnnotationValue
  case class Unknown() extends FriendlyAnnotationValue
}

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
