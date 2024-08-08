package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.BindingTag

trait Tagging[Self] extends Any {
  def tagged(tags: BindingTag*): Self

  def confined: Self = tagged(BindingTag.Confined)
  def exposed: Self = tagged(BindingTag.Exposed)
}
