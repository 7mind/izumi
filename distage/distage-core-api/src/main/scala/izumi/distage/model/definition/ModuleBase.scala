package izumi.distage.model.definition

import cats.Hash
import cats.kernel.{BoundedSemilattice, PartialOrder}
import izumi.distage.model.definition.ModuleBaseInstances.{CatsBoundedSemilattice, CatsPartialOrderHash, ModuleBaseSemilattice}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.platform.language.unused

import scala.collection.immutable.ListSet

trait ModuleBase {
  type Self <: ModuleBase
  def bindings: Set[Binding]

  final def keys: Set[DIKey] = bindings.map(_.key)

  override final def hashCode(): Int = bindings.hashCode()
  override final def equals(obj: Any): Boolean = obj match {
    case m: ModuleBase =>
      m.bindings == this.bindings
    case _ =>
      false
  }
  override final def toString: String = bindings.mkString(s"Module(",", ",")")
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

  implicit final class ModuleDefOps[S <: ModuleBase, T <: ModuleBase.Aux[T]](private val module: S)(implicit l: Lub[S, S#Self, T], T: ModuleMake[T]) {
    def map(f: Binding => Binding): T = {
      T.make(module.bindings.map(f))
    }

    def foldWith[B](b: B)(f: (Binding, B) => (Binding, B)): (T, B) = {
      val (bindings, fold) = module.bindings.foldLeft(List.empty[Binding] -> b) {
        case ((list, acc), bind) =>
          val (l, acc1) = f(bind, acc)
          (l :: list) -> acc1
      }
      T.make(bindings.toSet) -> fold
    }

    def flatMap(f: Binding => Iterable[Binding]): T = {
      T.make(module.bindings.flatMap(f))
    }
  }

  implicit final class ModuleDefMorph(private val module: ModuleBase) extends AnyVal {
    def morph[T <: ModuleBase](implicit T: ModuleMake[T]): T = {
      T.make(module.bindings)
    }
  }

  implicit final class ModuleDefCombine[S <: ModuleBase, T <: ModuleBase.Aux[T]](private val module: S)(implicit l: Lub[S, S#Self, T], T: ModuleMake[T]) {
    def ++(that: ModuleBase): T = {
      val theseBindings = module.bindings.toSeq
      val thoseBindings = that.bindings.toSeq

      T.make(tagwiseMerge(theseBindings ++ thoseBindings))
    }

    def :+(binding: Binding): T = {
      module ++ T.make(Set(binding))
    }

    def +:(binding: Binding): T = {
      T.make(Set(binding)) ++ module
    }

    def --(that: ModuleBase): T = {
      drop(that.keys)
    }

    def preserveOnly(preserve: Set[DIKey]): T = {
      val filtered = module.bindings.filterNot(b => preserve.contains(b.key))
      T.make(filtered)
    }

    def drop(ignored: Set[DIKey]): T = {
      val filtered = module.bindings.filterNot(b => ignored.contains(b.key))
      T.make(filtered)
    }

    def overridenBy(that: ModuleBase): T = {
      T.make(mergePreserve(module.bindings, that.bindings)._2)
    }

    private[this] def mergePreserve(existing: Set[Binding], overriding: Set[Binding]): (Set[DIKey], Set[Binding]) = {
      // FIXME: a hack to support tag merging
      def modulewiseMerge(a: Set[Binding], b: Set[Binding]): Set[Binding] = {
        (T.make(a) ++ T.make(b)).bindings
      }

      val existingIndex = existing.map(b => b.key -> b).toMultimap
      val newIndex = overriding.map(b => b.key -> b).toMultimap
      val mergedKeys = existingIndex.keySet ++ newIndex.keySet

      val merged = mergedKeys
        .flatMap {
          k =>
            val existingMappings = existingIndex.getOrElse(k, Set.empty)
            val newMappings = newIndex.getOrElse(k, Set.empty)

            if (existingMappings.isEmpty) {
              newMappings
            } else if (newMappings.isEmpty) {
              existingMappings
            } else {
              // merge tags wrt strange Binding equals
              val newMappingsGroups = newMappings.map(_.group)
              val existingInNewMappings = existingMappings.filter(newMappingsGroups contains _.group)
              modulewiseMerge(newMappings, existingInNewMappings)
            }
        }

      (mergedKeys, merged)
    }
  }

  final class Lub[-A, -B, Out](private val dummy: Boolean = false) extends AnyVal
  object Lub {
    implicit def lub[T]: Lub[T, T, T] = new Lub[T, T, T]
  }

  private[definition] def tagwiseMerge(bs: Iterable[Binding]): Set[Binding] = {
    val grouped = bs.groupBy(_.group)

    ListSet.newBuilder.++= {
      grouped
        .map {
          case (_, v) =>
            //assert(v.forall(_.key == k.key), s"${k.key}, ${v.map(_.key)}")
            v.reduce(_ addTags _.tags)
        }
    }.result()
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def optionalCatsPartialOrderHashForModuleBase[T <: ModuleBase, K[_] : CatsPartialOrderHash]: K[T] = {
    import cats.instances.set._

    new PartialOrder[T] with Hash[T] {
      override def partialCompare(x: T, y: T): Double = PartialOrder[Set[Binding]].partialCompare(x.bindings, y.bindings)
      override def hash(x: T): Int = x.hashCode()
      override def eqv(x: T, y: T): Boolean = x == y
    }.asInstanceOf[K[T]]
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def optionalCatsSemilatticeForModuleBase[T <: ModuleBase.Aux[T] : ModuleMake, K[_] : CatsBoundedSemilattice]: K[T] =
    new ModuleBaseSemilattice[T].asInstanceOf[K[T]]

}

private object ModuleBaseInstances {

  final class ModuleBaseSemilattice[T <: ModuleBase.Aux[T] : ModuleMake] extends BoundedSemilattice[T] {
    def empty: T = ModuleMake[T].empty
    def combine(x: T, y: T): T = x ++ y
  }

  sealed abstract class CatsBoundedSemilattice[K[_]]
  object CatsBoundedSemilattice {
    @inline implicit final def get: CatsBoundedSemilattice[BoundedSemilattice] = null
  }

  type PartialOrderHash[T] = PartialOrder[T] with Hash[T]
  sealed abstract class CatsPartialOrderHash[K[_]]
  object CatsPartialOrderHash {
    @inline implicit final def get[K[_]](implicit @unused guard: CatsBoundedSemilattice[K]): CatsPartialOrderHash[PartialOrderHash] = null
  }

}

