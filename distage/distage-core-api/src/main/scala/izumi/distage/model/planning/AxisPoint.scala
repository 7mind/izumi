package izumi.distage.model.planning

import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.MutableMultiMap
import izumi.fundamentals.platform.strings.IzString.toRichString

import scala.collection.compat.*

final case class AxisPoint(axis: String, value: String) {
  override def toString: String = s"$axis:$value"
}

object AxisPoint {
  def apply(kv: (String, String)): AxisPoint = AxisPoint(kv._1, kv._2)

  implicit final class SetOps(private val value: IterableOnce[AxisPoint]) extends AnyVal {
    def toActivationMap: Map[String, String] = value.iterator.map(v => v.axis -> v.value).toMap
    def toActivationMultimapMut: MutableMultiMap[String, String] = value.iterator.map(v => v.axis -> v.value).toMultimapMut
  }

  def parseAxisPoint(s: String): AxisPoint = {
    try {
      AxisPoint(s.split2(':'))
    } catch {
      case _: NoSuchElementException | _: UnsupportedOperationException =>
        throw new IllegalArgumentException(
          s"""Invalid axis choice syntax in `$s`
             |Valid syntax:
             |
             |  - axisName:axisChoice
             |
             |e.g. repo:prod, mode:test, scene:
             |""".stripMargin
        )
    }
  }
}
