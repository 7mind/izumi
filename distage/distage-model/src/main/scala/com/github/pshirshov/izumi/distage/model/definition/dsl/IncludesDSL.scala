package com.github.pshirshov.izumi.distage.model.definition.dsl

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleBase}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard

import scala.collection.mutable

trait IncludesDSL {
  import IncludesDSL._
  final private[this] val mutableRetaggedIncludes: mutable.ArrayBuffer[IncludeApplyTags] = _initialTaggedIncludes
  final private[this] val mutableAsIsIncludes: mutable.ArrayBuffer[Include] = _initialIncludes

  final private[definition] def retaggedIncludes: List[Binding] = mutableRetaggedIncludes.flatMap(_.bindings).toList
  final private[definition] def asIsIncludes: List[Binding] = mutableAsIsIncludes.flatMap(_.bindings).toList

  protected def _initialIncludes: mutable.ArrayBuffer[Include] = mutable.ArrayBuffer.empty
  protected def _initialTaggedIncludes: mutable.ArrayBuffer[IncludeApplyTags] = mutable.ArrayBuffer.empty

  /** Add all bindings in `that` module into `this` module
    *
    * WON'T add global tags from [[TagsDSL#tag]] to included bindings.
    **/
  final protected def include(that: ModuleBase): Unit = discard {
    mutableAsIsIncludes += Include(that.bindings)
  }

  /** Add all bindings in `that` module into `this` module
    *
    * WILL add global tags from [[TagsDSL#tag]] to included bindings.
    **/
  final protected def includeApplyTags(that: ModuleBase): Unit = discard {
    mutableRetaggedIncludes += IncludeApplyTags(that.bindings)
  }
}

object IncludesDSL {
  sealed trait IncludeRef
  final case class IncludeApplyTags(bindings: Set[Binding]) extends IncludeRef
  final case class Include(bindings: Set[Binding])  extends IncludeRef
}
