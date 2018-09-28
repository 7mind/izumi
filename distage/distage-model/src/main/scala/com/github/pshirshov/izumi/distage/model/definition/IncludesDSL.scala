package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard

import scala.collection.mutable

trait IncludesDSL {
  import IncludesDSL._
  final private[this] val mutableTaggedIncludes: mutable.ArrayBuffer[IncludeApplyTags] = _initialTaggedIncludes
  final private[this] val mutableIncludes: mutable.ArrayBuffer[Include] = _initialIncludes

  final protected[definition] def asIsIncludes: List[Binding] = mutableTaggedIncludes.flatMap(_.bindings).toList
  final protected[definition] def retaggedIncludes: List[Binding] = mutableIncludes.flatMap(_.bindings).toList

  protected def _initialIncludes: mutable.ArrayBuffer[Include] = mutable.ArrayBuffer.empty
  protected def _initialTaggedIncludes: mutable.ArrayBuffer[IncludeApplyTags] = mutable.ArrayBuffer.empty

  /** Add all bindings in `that` module into `this` module
    *
    * WON'T add global tags from [[TagsDSL#tag]] to included bindings.
    **/
  final protected def include(that: ModuleBase): Unit = discard {
    mutableIncludes += Include(that.bindings)
  }

  /** Add all bindings in `that` module into `this` module
    *
    * WILL add global tags from [[TagsDSL#tag]] to included bindings.
    **/
  final protected def includeApplyTags(that: ModuleBase): Unit = discard {
    mutableTaggedIncludes += IncludeApplyTags(that.bindings)
  }
}

object IncludesDSL {
  sealed trait IncludeRef
  final case class IncludeApplyTags(bindings: Set[Binding]) extends IncludeRef
  final case class Include(bindings: Set[Binding])  extends IncludeRef
}
