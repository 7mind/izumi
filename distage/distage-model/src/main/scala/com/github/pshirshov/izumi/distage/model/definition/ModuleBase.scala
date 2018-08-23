package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.exceptions.ModuleMergeException
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._

trait ModuleBase {
  def bindings: Set[Binding]

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ModuleBase => bindings == that.bindings
    case _ => false
  }

  override def hashCode: Int = bindings.hashCode()

  override def toString: String = bindings.toString()
}

object ModuleBase {
  implicit object ModuleOps extends ModuleApi[ModuleBase] {
    override def empty: ModuleBase = Module.empty

    override def simple(bindings: Set[Binding]): ModuleBase = Module.simple(bindings)
  }

  implicit final class ModuleDefSeqExt[T <: ModuleBase : ModuleApi](private val defs: Seq[T]) {
    private val T = implicitly[ModuleApi[T]]
    def merge: T = {
      defs.reduceLeftOption[T](_ ++ _).getOrElse(T.empty)
    }

    def overrideLeft: T = {
      defs.reduceOption[T](_ overridenBy _).getOrElse(T.empty)
    }
  }

  implicit final class ModuleDefOps[T <: ModuleBase : ModuleApi](private val moduleDef: T) {
    private val T = implicitly[ModuleApi[T]]
    def map(f: Binding => Binding): T = {
      T.simple(moduleDef.bindings.map(f))
    }
  }

  implicit final class ModuleDefCombine[T <: ModuleBase : ModuleApi](private val moduleDef: T) {
    private val T = implicitly[ModuleApi[T]]

    def ++(that: ModuleBase): T = {
      // FIXME: a hack to support tag merging

      // FIXME: this makes sense because Bindings equals/hashcode ignores `tags` field
      val theseBindings = moduleDef.bindings.toSeq
      val thoseBindings = that.bindings.toSeq

      T.simple(tagwiseMerge(theseBindings ++ thoseBindings))
    }

    def :+(binding: Binding): T = {
      moduleDef ++ T.simple(Set(binding))
    }

    def +:(binding: Binding): T = {
      T.simple(Set(binding)) ++ moduleDef
    }

    def overridenBy(that: ModuleBase): T = {
      // we replace existing items in-place and appending new at the end
      // set bindings should not be touched

      // FIXME: a hack to support tag merging
      def modulewiseMerge(a: Set[Binding], b: Set[Binding]): Set[Binding] =
        (T.simple(a) ++ T.simple(b)).bindings

      val existingSetElements = moduleDef.bindings.collect({ case b: SetElementBinding[_] => b: Binding })
      val newSetElements = that.bindings.collect({ case b: SetElementBinding[_] => b: Binding })
      val mergedSetElements = modulewiseMerge(existingSetElements, newSetElements)

      val existingSets = moduleDef.bindings.collect({ case b: EmptySetBinding[_] => b: Binding })
      val newSets = that.bindings.collect({ case b: EmptySetBinding[_] => b: Binding })
      val mergedSets = modulewiseMerge(existingSets, newSets)

      val mergedSetOperations = mergedSets ++ mergedSetElements

      val setOps = mergedSetOperations.map(_.key)

      val existingSingletons = moduleDef.bindings.collect({ case b: SingletonBinding[_] => b: Binding })
      val newSingletons = that.bindings.collect({ case b: SingletonBinding[_] => b: Binding })

      val existingIndex = existingSingletons.map(b => b.key -> b).toMultimap
      val newIndex = newSingletons.map(b => b.key -> b).toMultimap
      val mergedKeys = existingIndex.keySet ++ newIndex.keySet

      val badKeys = setOps.intersect(mergedKeys)
      if (badKeys.nonEmpty) {
        throw new ModuleMergeException(s"Cannot override bindings, unsolvable conflicts: $badKeys", badKeys)
      }

      val mergedSingletons = mergedKeys.flatMap {
        k =>
          val existingMappings = existingIndex.getOrElse(k, Set.empty)
          val newMappings = newIndex.getOrElse(k, Set.empty)

          if (existingMappings.isEmpty) {
            newMappings
          } else if (newMappings.isEmpty) {
            existingMappings
          } else {
            // merge tags wrt strange Binding equals
            modulewiseMerge(newMappings, existingMappings intersect newMappings)
          }
      }

      T.simple(modulewiseMerge(mergedSingletons, mergedSetOperations))
    }
  }

  private[definition] def tagwiseMerge(bs: Iterable[Binding]): Set[Binding] =
  // Using lawless equals/hashcode
    bs.groupBy(identity).values.map {
      _.reduce(_ addTags _.tags)
    }.toSet

}




