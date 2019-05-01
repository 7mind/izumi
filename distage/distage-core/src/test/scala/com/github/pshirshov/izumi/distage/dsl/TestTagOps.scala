package com.github.pshirshov.izumi.distage.dsl

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisValue
import com.github.pshirshov.izumi.distage.model.definition.{Axis, AxisBase, Binding, BindingTag}
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import distage.ModuleBase

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

  implicit class BindingExt(b: Set[Binding]) {
    def ===(o: Set[Binding]): Boolean = {
      b.map(_.group) == o.map(_.group)
    }
  }

  implicit class ModuleExt(b: ModuleBase) {
    def ===(o: ModuleBase): Boolean = {
      b.bindings === o.bindings
    }
  }
}
