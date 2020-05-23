package izumi.distage.model.definition

import cats.Hash
import cats.kernel.{BoundedSemilattice, PartialOrder}
import izumi.distage.model.definition.ModuleBaseInstances.{CatsBoundedSemilattice, CatsPartialOrderHash, ModuleBaseSemilattice}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.platform.language.unused

import scala.collection.immutable.ListSet

trait ModuleBase extends ModuleBaseInstances {
  def bindings: Set[Binding]
  final def keys: Set[DIKey] = bindings.map(_.key)

  override final def hashCode(): Int = bindings.hashCode()
  override final def equals(obj: Any): Boolean = obj match {
    case m: ModuleBase =>
      m.bindings == this.bindings
    case _ =>
      false
  }
  override final def toString: String = bindings.iterator.map(_.toString).mkString("\n", "\n", "")
}

object ModuleBase {
  def empty: ModuleBase = make(Set.empty)

  def make(bindings: Set[Binding]): ModuleBase = {
    val b = bindings
    new ModuleBase {
      override val bindings: Set[Binding] = b
    }
  }

  implicit val moduleBaseApi: ModuleMake[ModuleBase] = ModuleBase.make

  implicit final class ModuleDefSeqExt[S <: ModuleBase](private val defs: Iterable[S]) extends AnyVal {
    def merge[T <: ModuleBase](implicit T: ModuleMake.Aux[S, T]): T = {
      defs.foldLeft[T](T.empty)(_ ++ _)
    }

    def overrideLeft[T <: ModuleBase](implicit T: ModuleMake.Aux[S, T]): T = {
      defs.foldLeft[T](T.empty)(_ overridenBy _)
    }
  }

  implicit final class ModuleDefOps[S <: ModuleBase](private val module: S) extends AnyVal {
    def map[T <: ModuleBase](f: Binding => Binding)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.map(f))
    }

    def foldLeft[B](b: B)(f: (B, Binding) => B): B = {
      module.bindings.foldLeft(b) {
        case (acc, bind) => f(acc, bind)
      }
    }

    def foldLeftWith[B, T <: ModuleBase](b: B)(f: (B, Binding) => (B, Binding))(implicit T: ModuleMake.Aux[S, T]): (B, T) = {
      val (bindings, fold) = foldLeft(List.empty[Binding] -> b) {
        case ((list, acc), bind) =>
          val (acc1, el) = f(acc, bind)
          (el :: list) -> acc1
      }
      fold -> T.make(bindings.toSet)
    }

    def flatMap[T <: ModuleBase](f: Binding => Iterable[Binding])(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.flatMap(f))
    }
  }

  implicit final class ModuleDefMorph(private val module: ModuleBase) extends AnyVal {
    def morph[T <: ModuleBase](implicit T: ModuleMake[T]): T = {
      T.make(module.bindings)
    }
  }

  implicit final class ModuleDefCombine[S <: ModuleBase](private val module: S) extends AnyVal {
    def ++[T <: ModuleBase](that: ModuleBase)(implicit T: ModuleMake.Aux[S, T]): T = {
      val theseBindings = module.bindings.toSeq
      val thoseBindings = that.bindings.toSeq

      T.make(tagwiseMerge(theseBindings ++ thoseBindings))
    }

    def :+[T <: ModuleBase](binding: Binding)(implicit T: ModuleMake.Aux[S, T]): T = {
      module ++ T.make(Set(binding))
    }

    def +:[T <: ModuleBase](binding: Binding)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(Set(binding)) ++ module
    }

    def --[T <: ModuleBase](that: ModuleBase)(implicit T: ModuleMake.Aux[S, T]): T = {
      drop(that.keys)
    }

    def preserveOnly[T <: ModuleBase](preserve: Set[DIKey])(implicit T: ModuleMake.Aux[S, T]): T = {
      val filtered = module.bindings.filterNot(b => preserve.contains(b.key))
      T.make(filtered)
    }

    def drop[T <: ModuleBase](ignored: Set[DIKey])(implicit T: ModuleMake.Aux[S, T]): T = {
      val filtered = module.bindings.filterNot(b => ignored.contains(b.key))
      T.make(filtered)
    }

    def overridenBy[T <: ModuleBase](that: ModuleBase)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(mergePreserve[T](module.bindings, that.bindings)._2)
    }

    private[this] def mergePreserve[T <: ModuleBase](existing: Set[Binding], overriding: Set[Binding])(implicit T: ModuleMake.Aux[S, T]): (Set[DIKey], Set[Binding]) = {
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

  private[definition] def tagwiseMerge(bs: Iterable[Binding]): Set[Binding] = {
    val grouped = bs.groupBy(_.group)

    // Use ListSet for more deterministic order, e.g. have the same bindings order between app runs for more comfortable debugging
    ListSet
      .newBuilder.++= {
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
  implicit def optionalCatsPartialOrderHashForModuleBase[T <: ModuleBase, K[_]: CatsPartialOrderHash]: K[T] = {
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
  implicit def optionalCatsSemilatticeForModuleBase[T <: ModuleBase: ModuleMake, K[_]: CatsBoundedSemilattice]: K[T] =
    new ModuleBaseSemilattice[T].asInstanceOf[K[T]]

}

private[definition] sealed trait ModuleBaseInstances

object ModuleBaseInstances {

  // emulate bivariance for ModuleMake. The only purpose of the first parameter is to initiate
  // the search in its companion object, otherwise the parameter should be ignored when deciding
  // whether instances are subtypes of each other (aka bivariance)
  @inline implicit final def makeSelf[T <: ModuleBase](implicit T: ModuleMake.Aux[Nothing, T]): ModuleMake[T] =
    T.asInstanceOf[ModuleMake[T]]

  final class ModuleBaseSemilattice[T <: ModuleBase: ModuleMake] extends BoundedSemilattice[T] {
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
