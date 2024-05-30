package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.dsl.IncludesDSL.{Include, TagMergePolicy}
import izumi.distage.model.definition.{Binding, BindingTag, ModuleBase}
import izumi.fundamentals.platform.language.Quirks.discard

import scala.collection.mutable

trait IncludesDSL {
  final private[this] val mutableAsIsIncludes: mutable.ArrayBuffer[Include] = _initialIncludes

  protected[this] def _initialIncludes: mutable.ArrayBuffer[Include] = mutable.ArrayBuffer.empty

  final private[dsl] def includes: Iterator[Include] = mutableAsIsIncludes.iterator

  /** Add all bindings in `that` module into `this` module
    *
    * WON'T add global tags from [[TagsDSL#tag]] to included bindings.
    */
  final protected[this] def include(that: ModuleBase, tagMergeStrategy: TagMergePolicy = TagMergePolicy.MergePreferInner): Unit = discard {
    mutableAsIsIncludes += Include(that, tagMergeStrategy)
  }

  /** Add all bindings in `that` module into `this` module
    *
    * WILL add global tags from [[TagsDSL#tag]] to included bindings.
    */
  @deprecated("Outer module's tags are now added to included module by default, use regular `include`", "1.2.9")
  final protected[this] def includeApplyTags(that: ModuleBase): Unit = {
    include(that, TagMergePolicy.MergePreferInner)
  }

  @deprecated("bincompat", "1.2.9")
  final private[dsl] def include(that: ModuleBase): Unit = {
    include(that, TagMergePolicy.MergePreferInner)
  }
}

object IncludesDSL {
  final case class Include(bindings: ModuleBase, tagMergePolicy: TagMergePolicy) {

    def interpret(frozenOuterTags: Set[BindingTag]): Iterator[Binding] = {

      def excludeTagsOnOverlap(allTags: Set[BindingTag], excludeOnOverlap: Set[BindingTag]): Set[BindingTag] = {
        val overlaps = allTags.groupBy { case BindingTag.AxisTag(choice) => choice.axis; case _ => null }
        val tagsToRemove = overlaps.iterator.flatMap {
          case (null, _) => Nil
          case (_, overlappingTags) if overlappingTags.size > 1 => overlappingTags.intersect(excludeOnOverlap)
          case _ => Nil
        }
        allTags -- tagsToRemove
      }

      val memoMap = mutable.HashMap.empty[Set[BindingTag], Set[BindingTag]]

      tagMergePolicy match {
        case TagMergePolicy.MergePreferInner =>
          bindings.iterator.map(_.modifyTags {
            tags =>
              memoMap.getOrElseUpdate(tags, excludeTagsOnOverlap(allTags = tags ++ frozenOuterTags, excludeOnOverlap = frozenOuterTags))
          })
        case TagMergePolicy.MergePreferOuter =>
          bindings.iterator.map(_.modifyTags {
            tags =>
              memoMap.getOrElseUpdate(tags, excludeTagsOnOverlap(allTags = tags ++ frozenOuterTags, excludeOnOverlap = tags))
          })
        case TagMergePolicy.MergePreferNewWith(newTags) =>
          bindings.iterator.map(_.modifyTags {
            tags =>
              memoMap.getOrElseUpdate(
                tags, {
                  val existingTags = tags ++ frozenOuterTags
                  excludeTagsOnOverlap(allTags = existingTags ++ newTags, excludeOnOverlap = existingTags)
                },
              )
          })
        case TagMergePolicy.MergePreferExistingWith(newTags) =>
          bindings.iterator.map(_.modifyTags {
            tags =>
              memoMap.getOrElseUpdate(tags, excludeTagsOnOverlap(allTags = tags ++ frozenOuterTags ++ newTags, excludeOnOverlap = newTags))
          })
        case TagMergePolicy.UseOnlyInner =>
          bindings.iterator
        case TagMergePolicy.UseOnlyOuter =>
          bindings.iterator.map(_.withTags(frozenOuterTags))
        case TagMergePolicy.ReplaceWith(newTags) =>
          bindings.iterator.map(_.withTags(newTags))
      }
    }

  }

  sealed trait TagMergePolicy
  object TagMergePolicy {

    /** {{{
      * new ModuleDef {
      *   tag(A.X, B.C)
      *   include(new ModuleDef {
      *     make[X].tagged(A.Y)
      *   }, TagMergePolicy.MergePreferInner)
      * }
      * // result:
      * make[X].tagged(A.Y, B.C)
      * }}}
      */
    case object MergePreferInner extends TagMergePolicy

    /** {{{
      * new ModuleDef {
      *   tag(A.X, B.C)
      *   include(new ModuleDef {
      *     make[X].tagged(A.Y)
      *   }, TagMergePolicy.MergePreferOuter)
      * }
      * // result:
      * make[X].tagged(A.X, B.C)
      * }}}
      */
    case object MergePreferOuter extends TagMergePolicy

    /**
      * Do not modify tags in the included module), add its bindings as is.
      *
      * {{{
      * new ModuleDef {
      *   tag(A.X)
      *   include(new ModuleDef {
      *     make[X].tagged(B.C)
      *   }, TagMergePolicy.UseOnlyInner)
      * }
      * // result:
      * make[X].tagged(B.C)
      * }}}
      */
    case object UseOnlyInner extends TagMergePolicy

    /** {{{
      * new ModuleDef {
      *   tag(A.X)
      *   include(new ModuleDef {
      *     make[X].tagged(B.C)
      *   }, TagMergePolicy.UseOnlyOuter)
      * }
      * // result:
      * make[X].tagged(A.X)
      * }}}
      */
    case object UseOnlyOuter extends TagMergePolicy

    /** {{{
      * new ModuleDef {
      *   tag(A.X)
      *   include(new ModuleDef {
      *     make[X].tagged(B.C)
      *   }, TagMergePolicy.ReplaceWith(Set(C.G)))
      * }
      * // result:
      * make[X].tagged(C.G)
      * }}}
      */
    final case class ReplaceWith(newTags: Set[BindingTag]) extends TagMergePolicy

    /** {{{
      * new ModuleDef {
      *   tag(A.X)
      *   include(new ModuleDef {
      *     make[X].tagged(B.C)
      *   }, TagMergePolicy.MergePreferNewWith(Set(B.D, C.G)))
      * }
      * // result:
      * make[X].tagged(A.X, B.D, C.G)
      * }}}
      */
    final case class MergePreferNewWith(newTags: Set[BindingTag]) extends TagMergePolicy

    /** {{{
      * new ModuleDef {
      *   tag(A.X)
      *   include(new ModuleDef {
      *     make[X].tagged(B.C)
      *   }, TagMergePolicy.MergePreferExistingWith(Set(B.D, C.G)))
      * }
      * // result:
      * make[X].tagged(A.X, B.C, C.G)
      * }}}
      */
    final case class MergePreferExistingWith(newTags: Set[BindingTag]) extends TagMergePolicy

  }

}
