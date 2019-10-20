package izumi.distage.model.definition

import cats.Hash
import cats.kernel.{BoundedSemilattice, PartialOrder}
import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.ModuleBaseInstances.{CatsBoundedSemilattice, CatsPartialOrderHash, ModuleBaseSemilattice}
import izumi.distage.model.exceptions.ModuleMergeException
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import izumi.fundamentals.collections.IzCollections._

trait ModuleBase {
  def bindings: Set[Binding]
  type Self <: ModuleBase

  final def keys: Set[DIKey] = bindings.map(_.key)

  override final def hashCode(): Int = bindings.hashCode()
  override final def equals(obj: Any): Boolean = obj match {
    case m: ModuleBase =>
      m.bindings == this.bindings
    case _ =>
      false
  }
  override final def toString: String = bindings.toString()
}

object ModuleBase {
  type Aux[S] = ModuleBase {type Self <: S}

  implicit val moduleBaseApi: ModuleMake[ModuleBase] = {
    binds =>
      new ModuleBase {
        override val bindings: Set[Binding] = binds
      }
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

    def flatMap(f: Binding => Iterable[Binding]): T = {
      ModuleMake[T].make(moduleDef.bindings.flatMap(f))
    }

  }

  implicit final class ModuleDefMorph(private val moduleDef: ModuleBase) {
    def morph[T <: ModuleBase : ModuleMake]: T = {
      ModuleMake[T].make(moduleDef.bindings)
    }
  }

  implicit final class ModuleDefCombine[S <: ModuleBase, T <: ModuleBase.Aux[T]](val moduleDef: S)(implicit l: Lub[S, S#Self, T], T: ModuleMake[T]) {
    def ++(that: ModuleBase): T = {
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

    def drop(ignored: Set[DIKey]): T = {
      val filtered = moduleDef.bindings.filterNot(b => ignored.contains(b.key))
      T.make(filtered)
    }

    def overridenBy(that: ModuleBase): T = {
      // we replace existing items in-place and append new at the end
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
            modulewiseMerge(newMappings, existingMappings.filter(m => newMappings.map(_.group).contains(m.group)))
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
    bs.groupBy(_.group)
      .map {
        case (k, v) =>
          assert(v.forall(_.key == k.key))
          v.reduce(_ addTags _.tags)
      }
      .toSet
  }

  /** Optional instance via https://blog.7mind.io/no-more-orphans.html */
  implicit def optionalCatsPartialOrderHashForModuleBase[T <: ModuleBase, K[_]: CatsPartialOrderHash]: K[T] = {
    import cats.instances.set._

    new PartialOrder[T] with Hash[T] {
      override def partialCompare(x: T, y: T): Double = PartialOrder[Set[Binding]].partialCompare(x.bindings, y.bindings)
      override def hash(x: T): Int = x.hashCode()
      override def eqv(x: T, y: T): Boolean = x == y
    }.asInstanceOf[K[T]]
  }

  /** Optional instance via https://blog.7mind.io/no-more-orphans.html */
  implicit def optionalCatsSemilatticeForModuleBase[T <: ModuleBase.Aux[T]: ModuleMake, K[_]: CatsBoundedSemilattice]: K[T] =
    new ModuleBaseSemilattice[T].asInstanceOf[K[T]]

}

private object ModuleBaseInstances {

  final class ModuleBaseSemilattice[T <: ModuleBase.Aux[T] : ModuleMake] extends BoundedSemilattice[T] {
    def empty: T = ModuleMake[T].empty

    def combine(x: T, y: T): T = x ++ y
  }

  sealed abstract class CatsBoundedSemilattice[K[_]]
  object CatsBoundedSemilattice {
    @inline implicit def get: CatsBoundedSemilattice[BoundedSemilattice] = null
  }

  type PartialOrderHash[T] = PartialOrder[T] with Hash[T]
  sealed abstract class CatsPartialOrderHash[K[_]]
  object CatsPartialOrderHash {
    @inline implicit def get[K[_]](implicit @deprecated("unused", "unused") guard: CatsBoundedSemilattice[K]): CatsPartialOrderHash[PartialOrderHash] = null
  }

}

