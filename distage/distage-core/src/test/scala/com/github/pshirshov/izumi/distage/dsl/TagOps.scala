package com.github.pshirshov.izumi.distage.dsl

import com.github.pshirshov.izumi.distage.model.definition.BindingTag

object TagOps {
  implicit class TagConversions(private val tags: scala.collection.immutable.Set[BindingTag]) extends AnyVal {
    def strings: Set[String] = tags.collect({ case BindingTag.StringTag(v) => v })
  }
}
