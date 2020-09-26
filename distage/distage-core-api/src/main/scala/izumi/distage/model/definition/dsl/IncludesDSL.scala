package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.dsl.IncludesDSL.{Include, IncludeApplyTags}
import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.fundamentals.platform.language.Quirks.discard

import scala.collection.mutable

trait IncludesDSL {
  final private[this] val mutableRetaggedIncludes: mutable.ArrayBuffer[IncludeApplyTags] = _initialTaggedIncludes
  final private[this] val mutableAsIsIncludes: mutable.ArrayBuffer[Include] = _initialIncludes

  final private[dsl] def retaggedIncludes: List[Binding] = mutableRetaggedIncludes.flatMap(_.bindings).toList
  final private[dsl] def asIsIncludes: List[Binding] = mutableAsIsIncludes.flatMap(_.bindings).toList

  protected[this] def _initialIncludes: mutable.ArrayBuffer[Include] = mutable.ArrayBuffer.empty
  protected[this] def _initialTaggedIncludes: mutable.ArrayBuffer[IncludeApplyTags] = mutable.ArrayBuffer.empty

  /** Add all bindings in `that` module into `this` module
    *
    * WON'T add global tags from [[TagsDSL#tag]] to included bindings.
    */
  final protected[this] def include(that: ModuleBase): Unit = discard {
    mutableAsIsIncludes += Include(that.bindings)
  }

  /** Add all bindings in `that` module into `this` module
    *
    * WILL add global tags from [[TagsDSL#tag]] to included bindings.
    */
  final protected[this] def includeApplyTags(that: ModuleBase): Unit = discard {
    mutableRetaggedIncludes += IncludeApplyTags(that.bindings)
  }
}

object IncludesDSL {
  sealed trait IncludeRef
  final case class IncludeApplyTags(bindings: Set[Binding]) extends IncludeRef
  final case class Include(bindings: Set[Binding]) extends IncludeRef
}
