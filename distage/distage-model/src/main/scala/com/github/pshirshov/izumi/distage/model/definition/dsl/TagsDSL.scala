package com.github.pshirshov.izumi.distage.model.definition.dsl

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard

import scala.collection.mutable

trait TagsDSL {
  private[this] final val mutableTags: mutable.Set[BindingTag] = _initialTags

  protected def _initialTags: mutable.Set[BindingTag] = mutable.HashSet.empty

  final private[definition] def frozenTags: Set[BindingTag] = mutableTags.toSet

  /** Add `tags` to all bindings in this module, except [[IncludesDSL#include included]] bindings */
  final protected def tag(tags: BindingTag*): Unit = discard {
    mutableTags ++= tags
  }

}
