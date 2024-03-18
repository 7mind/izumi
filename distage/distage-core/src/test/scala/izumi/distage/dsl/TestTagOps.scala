package izumi.distage.dsl

import izumi.distage.model.definition.{Axis, BindingTag}

import scala.language.implicitConversions

object TestTagOps {

  implicit def apply(tag: String): BindingTag = TestAxis.TestTag(tag)

  object TestAxis extends Axis {
    override def name: String = "test"

    abstract class TestAxis extends AxisChoiceDef
    final case class TestTag(t: String) extends TestAxis {
      override def value: String = t
    }
  }

  implicit class TagConversions(private val tags: scala.collection.immutable.Set[BindingTag]) extends AnyVal {
    def strings: Set[String] = tags.collect { case BindingTag.AxisTag(v: TestAxis.TestTag) => v.t }
  }

}
