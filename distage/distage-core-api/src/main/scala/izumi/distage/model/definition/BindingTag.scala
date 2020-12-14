package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisChoice

import scala.language.implicitConversions

/** An attachment that can be added to a binding using its `.tagged` method */
trait BindingTag

object BindingTag {
  implicit def apply(tag: AxisChoice): BindingTag = AxisTag(tag)

  final case class AxisTag(choice: AxisChoice) extends BindingTag
}
