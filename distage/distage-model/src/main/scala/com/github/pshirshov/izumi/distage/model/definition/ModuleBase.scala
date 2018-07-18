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

  override def hashCode(): Int = bindings.hashCode()

  override def toString: String = bindings.toString()
}

object ModuleBase {

  implicit final class ModuleDefSeqExt(private val defs: Seq[ModuleBase]) extends AnyVal {
    def merge: ModuleBase = {
      defs.reduceLeftOption[ModuleBase](_ ++ _).getOrElse(SimpleModuleDef.empty)
    }

    def overrideLeft: ModuleBase = {
      defs.reduceOption[ModuleBase](_ overridenBy _).getOrElse(SimpleModuleDef.empty)
    }
  }

  implicit final class ModuleDefOps(private val moduleDef: ModuleBase) extends AnyVal {
    def map(f: Binding => Binding): ModuleBase = {
      SimpleModuleDef(moduleDef.bindings.map(f))
    }
  }

  implicit final class ModuleDefCombine(private val moduleDef: ModuleBase) extends AnyVal {
    def ++(that: ModuleBase): ModuleBase = {
      // FIXME: a hack to support tag merging

      // FIXME: this makes sense because Bindings equals/hashcode ignores `tags` field
      val sameThis = moduleDef.bindings intersect that.bindings
      val sameThat = that.bindings intersect moduleDef.bindings

      val newIntersection = sameThis.zip(sameThat).map { case (a, b) => b.addTags(a.tags) }

      SimpleModuleDef(moduleDef.bindings.diff(sameThis) ++ that.bindings.diff(sameThat) ++ newIntersection)
    }

    def :+(binding: Binding): ModuleBase = {
      moduleDef ++ SimpleModuleDef(Set(binding))
    }

    def +:(binding: Binding): ModuleBase = {
      SimpleModuleDef(Set(binding)) ++ moduleDef
    }

    def overridenBy(that: ModuleBase): ModuleBase = {
      // we replace existing items in-place and appending new at the end
      // set bindings should not be touched

      // FIXME: a hack to support tag merging
      def modulewiseMerge(a: Set[Binding], b: Set[Binding]): Set[Binding] =
        (SimpleModuleDef(a) ++ SimpleModuleDef(b)).bindings

      val existingSetElements = moduleDef.bindings.collect({case b: SetElementBinding[_] => b: Binding})
      val newSetElements = that.bindings.collect({case b: SetElementBinding[_] => b: Binding})
      val mergedSetElements = modulewiseMerge(existingSetElements, newSetElements)

      val existingSets = moduleDef.bindings.collect({case b: EmptySetBinding[_] => b: Binding})
      val newSets = that.bindings.collect({case b: EmptySetBinding[_] => b: Binding})
      val mergedSets = modulewiseMerge(existingSets, newSets)

      val mergedSetOperations = mergedSets ++ mergedSetElements

      val setOps = mergedSetOperations.map(_.key)

      val existingSingletons = moduleDef.bindings.collect({case b: SingletonBinding[_] => b: Binding})
      val newSingletons = that.bindings.collect({case b: SingletonBinding[_] => b: Binding})

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

      SimpleModuleDef(modulewiseMerge(mergedSingletons, mergedSetOperations))
    }
  }

}
