package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue
import izumi.fundamentals.collections.IzCollections.toRichMappings
import izumi.fundamentals.collections.MutableMultiMap
import izumi.fundamentals.platform.strings.IzString.toRichString

import scala.collection.compat._

trait Axis { self =>
  def name: String = getClass.getName.toLowerCase.split('.').last.split('$').last
  override final def toString: String = s"$name"

  abstract class AxisValueDef extends AxisValue {
    override val axis: Axis = self
  }
}

object Axis {

  final case class AxisPoint(axis: String, value: String) {
    override def toString: String = s"$axis:$value"
  }
  object AxisPoint {
    implicit final class SetOps(private val value: IterableOnce[AxisPoint]) extends AnyVal {
      def toActivationMap: Map[String, String] = value.iterator.map(v => v.axis -> v.value).toMap
      def toActivationMultimapMut: MutableMultiMap[String, String] = value.iterator.map(v => v.axis -> v.value).toMultimapMut
    }
  }

  trait AxisValue {
    def axis: Axis
    def value: String = getClass.getName.toLowerCase.split('.').last.split('$').last

    final def toAxisPoint: AxisPoint = AxisPoint(axis.name, value)

    override final def toString: String = s"$axis:$value"

    @deprecated("Renamed to `value`", "1.0")
    final def id: String = value
  }
  object AxisValue {
    def splitAxisValue(s: String): (String, String) = {
      s.split2(':')
    }
  }
}
