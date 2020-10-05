package izumi.distage.model.plan.repr

import izumi.distage.model.definition.{Binding, BindingTag, ImplDef}
import izumi.distage.model.reflection.DIKey.SetElementKey

trait BindingFormatter {
  def formatBinding(binding: Binding): String
  def formatImplDef(implDef: ImplDef): String
}

object BindingFormatter {
  def apply(keyFormatter: KeyFormatter) = new Impl(keyFormatter)

  class Impl(keyFormatter: KeyFormatter) extends BindingFormatter {
    import keyFormatter.formatKey

    override def formatBinding(binding: Binding): String = {
      binding match {
        case Binding.SingletonBinding(key, implementation, tags, origin, false) =>
          s"make[${formatKey(key)}].from(${formatImplDef(implementation)})${formatTags(tags)} ($origin)"
        case Binding.SingletonBinding(key, implementation, tags, origin, true) =>
          s"modify[${formatKey(key)}].by(${formatImplDef(implementation)})${formatTags(tags)} ($origin)"
        case Binding.SetElementBinding(SetElementKey(set, key, _), implementation, tags, origin) =>
          s"many[${formatKey(set)}].add[${formatKey(key)}].from(${formatImplDef(implementation)})${formatTags(tags)} ($origin)"
        case Binding.EmptySetBinding(key, tags, origin) =>
          s"many[${formatKey(key)}]${formatTags(tags)} ($origin)"
      }
    }

    override def formatImplDef(implDef: ImplDef): String = {
      implDef match {
        case ImplDef.ReferenceImpl(implType, key, weak) =>
          if (weak) s"weak[${formatKey(key)}]" else s"using[${formatKey(key)}: $implType]"
        case ImplDef.InstanceImpl(implType, instance) =>
          s"value($instance: $implType)"
        case ImplDef.ProviderImpl(_, function) =>
          s"call($function)"
        case ImplDef.EffectImpl(_, effectHKTypeCtor, effectImpl) =>
          s"effect[$effectHKTypeCtor](${formatImplDef(effectImpl)})"
        case ImplDef.ResourceImpl(_, effectHKTypeCtor, resourceImpl) =>
          s"allocate[$effectHKTypeCtor](${formatImplDef(resourceImpl)})"
      }
    }

    private[this] def formatTags(tags: Set[BindingTag]) = {
      if (tags.isEmpty) "" else s".tagged($tags)"
    }
  }
}
