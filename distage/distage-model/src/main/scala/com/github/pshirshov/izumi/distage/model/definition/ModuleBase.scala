package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.exceptions.ModuleMergeException
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

import scala.collection.immutable.ListSet

trait ModuleBase {
  def bindings: Set[Binding]

  final def keys: Set[DIKey] = bindings.map(_.key)

  type Self <: ModuleBase

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ModuleBase => bindings == that.bindings
    case _ => false
  }

  override def hashCode: Int = bindings.hashCode()

  override def toString: String = bindings.toString()
}

object ModuleBase {
  type Aux[S] = ModuleBase {type Self <: S}

  implicit val moduleBaseApi: ModuleMake[ModuleBase] = s => new ModuleBase {
    override val bindings: Set[Binding] = s
  }

  def empty: ModuleBase = make(Set.empty)

  def make(bindings: Set[Binding]): ModuleBase = {
    val b = bindings
    new ModuleBase {
      override val bindings: Set[Binding] = b
    }
  }

  implicit final class ModuleDefSeqExt[S <: ModuleBase, T <: ModuleBase.Aux[T]](private val defs: Iterable[S])(implicit l: Lub[S, S#Self, T], T: ModuleMake[T]) {
    def merge: T = {
      defs.foldLeft[T](T.empty)(_ ++ _)
    }

    def overrideLeft: T = {
      defs.foldLeft[T](T.empty)(_ overridenBy _)
    }
  }

  implicit final class ModuleDefOps[S <: ModuleBase, T <: ModuleBase.Aux[T]](val moduleDef: S)(implicit l: Lub[S, S#Self, T], T: ModuleMake[T]) {
    def map(f: Binding => Binding): T = {
      ModuleMake[T].make(moduleDef.bindings.map(f))
    }
  }

  implicit final class ModuleDefMorph(private val moduleDef: ModuleBase) {
    def morph[T <: ModuleBase : ModuleMake]: T = {
      ModuleMake[T].make(moduleDef.bindings)
    }
  }

  implicit final class ModuleDefCombine[S <: ModuleBase, T <: ModuleBase.Aux[T]](val moduleDef: S)(implicit l: Lub[S, S#Self, T], T: ModuleMake[T]) {
    // Order is important
    def ++(that: ModuleBase): T = {
      // TODO: a hack to support tag merging

      // TODO: this makes sense because Bindings equals/hashcode ignores `tags` field
      val theseBindings = moduleDef.bindings.toSeq
      val thoseBindings = that.bindings.toSeq

      T.make(tagwiseMerge(theseBindings ++ thoseBindings))
    }

    def :+(binding: Binding): T = {
      moduleDef ++ T.make(Set(binding))
    }

    def +:(binding: Binding): T = {
      T.make(Set(binding)) ++ moduleDef
    }

    def overridenBy(that: ModuleBase): T = {
      // we replace existing items in-place and appending new at the end
      // set bindings should not be touched

      // TODO: a hack to support tag merging
      def modulewiseMerge(a: Set[Binding], b: Set[Binding]): Set[Binding] =
        (T.make(a) ++ T.make(b)).bindings

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

      T.make(modulewiseMerge(mergedSingletons, mergedSetOperations))
    }
  }

  final class Lub[-A, -B, Out](private val dummy: Boolean = false) extends AnyVal

  object Lub {
    implicit def lub[T]: Lub[T, T, T] = new Lub[T, T, T]
  }

  private[definition] def tagwiseMerge(bs: Iterable[Binding]): Set[Binding] = {
    // Using lawless equals/hashcode
    bs.groupBy(identity)
      .values
      .map {
        _.reduce(_ addTags _.tags)
      }
      .to[ListSet]
  }

}




