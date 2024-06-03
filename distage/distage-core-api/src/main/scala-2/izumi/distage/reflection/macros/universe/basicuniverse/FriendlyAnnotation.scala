package izumi.distage.reflection.macros.universe.basicuniverse

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
