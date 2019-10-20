package izumi.distage.dsl

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.{Axis, AxisBase, BindingTag}
import izumi.fundamentals.platform.build.ExposedTestScope

import scala.language.implicitConversions

@ExposedTestScope
object TestTagOps {

  abstract class TestAxis()(implicit val axis: AxisBase) extends AxisValue

  implicit def apply(tag: String): BindingTag = TestAxis.TestTag(tag)

  object TestAxis extends Axis[TestAxis] {
    override def name: String = "test"

    case class TestTag(t: String) extends TestAxis {
      override def id: String = t
    }

  }

  implicit class TagConversions(private val tags: scala.collection.immutable.Set[BindingTag]) extends AnyVal {
    def strings: Set[String] = tags.collect({ case BindingTag.AxisTag(v: TestAxis.TestTag) => v.t })
  }

}
