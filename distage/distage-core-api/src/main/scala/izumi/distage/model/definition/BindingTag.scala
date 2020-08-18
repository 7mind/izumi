package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisValue

import scala.language.implicitConversions

/** An attachment that can be added to a binding using its `.tagged` method */
trait BindingTag

object BindingTag {
  implicit def apply(tag: AxisValue): BindingTag = AxisTag(tag)

  final case class AxisTag(choice: AxisValue) extends BindingTag
  final case class ConfTag(confPath: String) extends BindingTag
}
