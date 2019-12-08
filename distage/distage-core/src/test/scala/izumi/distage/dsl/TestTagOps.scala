package izumi.distage.dsl

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.{Axis, BindingTag}
import izumi.fundamentals.platform.build.ExposedTestScope

import scala.language.implicitConversions

@ExposedTestScope
object TestTagOps {

  def apply(tag: String): BindingTag = TestAxis.TestTag(tag)

  object TestAxis extends Axis {
    override def name: String = "test"

    abstract class TestAxis(implicit val axis: Axis) extends AxisValue
    final case class TestTag(t: String) extends TestAxis {
      override def id: String = t
    }
  }

  implicit class TagConversions(private val tags: scala.collection.immutable.Set[BindingTag]) extends AnyVal {
    def strings: Set[String] = tags.collect { case BindingTag.AxisTag(v: TestAxis.TestTag) => v.t }
  }

}
