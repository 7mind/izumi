package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.BindingTag
import izumi.fundamentals.platform.language.Quirks.discard

import scala.collection.mutable

trait TagsDSL {
  private[this] final val mutableTags: mutable.Set[BindingTag] = _initialTags
  protected[this] def _initialTags: mutable.Set[BindingTag] = mutable.HashSet.empty

  private[dsl] final def frozenTags: Set[BindingTag] = mutableTags.toSet

  /** Add `tags` to all bindings in this module, except for [[IncludesDSL#include included]] bindings */
  final protected[this] def tag(tags: BindingTag*): Unit = discard {
    mutableTags ++= tags
  }
}
