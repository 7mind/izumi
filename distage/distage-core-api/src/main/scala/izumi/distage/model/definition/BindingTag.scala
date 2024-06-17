package izumi.distage.model.definition

import izumi.distage.model.definition.Axis.AxisChoice
import izumi.fundamentals.platform.cache.CachedProductHashcode

import scala.language.implicitConversions

/** An attachment that can be added to a binding using its `.tagged` method
  *
  * @note an inheritor of BindingTag must be an immutable case class
  *       and all of its fields must be used in `equals` / `hashCode`.
  */
trait BindingTag extends CachedProductHashcode { this: Product => }

object BindingTag {
  implicit def apply(tag: AxisChoice): BindingTag = AxisTag(tag)

  final case class AxisTag(choice: AxisChoice) extends BindingTag
}
