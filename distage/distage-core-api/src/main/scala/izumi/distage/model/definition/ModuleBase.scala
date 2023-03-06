package izumi.distage.model.definition

import cats.Hash
import cats.kernel.{BoundedSemilattice, PartialOrder}
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.orphans.{`cats.kernel.BoundedSemilattice`, `cats.kernel.PartialOrder with cats.kernel.Hash`}
import izumi.fundamentals.platform.cache.CachedHashcode

import scala.annotation.unused

trait ModuleBase extends CachedHashcode {
  def bindings: Set[Binding]
  def iterator: Iterator[Binding] = bindings.iterator

  final def keys: Set[DIKey] = keysIterator.toSet
  def keysIterator: Iterator[DIKey] = iterator.map(_.key)

  override final protected def hash: Int = bindings.hashCode()

  override final def equals(obj: Any): Boolean = obj match {
    case m: ModuleBase =>
      m.bindings == this.bindings
    case _ =>
      false
  }
  override final def toString: String = bindings.iterator.map(_.toString).mkString("\n", "\n", "")
}

object ModuleBase extends ModuleBaseLowPriorityInstances {
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
      T.make(defs.foldLeft(Iterator.empty: Iterator[Binding])(_ ++ _.iterator).toSet)
    }

    def overrideLeft[T <: ModuleBase](implicit T: ModuleMake.Aux[S, T]): T = {
      defs.foldLeft[T](T.empty)(_.overriddenBy[T](_))
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
      T.make(module.bindings ++ that.bindings)
    }

    def :+[T <: ModuleBase](binding: Binding)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings + binding)
    }

    def +:[T <: ModuleBase](binding: Binding)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings + binding)
    }

    def --[T <: ModuleBase](that: ModuleBase)(implicit T: ModuleMake.Aux[S, T]): T = {
      drop(that.keys)
    }

    def --[T <: ModuleBase](ignored: Set[DIKey])(implicit T: ModuleMake.Aux[S, T]): T = {
      drop(ignored)
    }

    def -[T <: ModuleBase](ignored: DIKey)(implicit T: ModuleMake.Aux[S, T]): T = {
      drop(ignored)
    }

    def filter[T <: ModuleBase](f: DIKey => Boolean)(implicit T: ModuleMake.Aux[S, T]): T = {
      filterBindings(f apply _.key)
    }

    def filterBindings[T <: ModuleBase](f: Binding => Boolean)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.filter(f))
    }

    def preserveOnly[T <: ModuleBase](preserve: Set[DIKey])(implicit T: ModuleMake.Aux[S, T]): T = {
      filter(preserve)
    }

    def drop[T <: ModuleBase](ignored: Set[DIKey])(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.filterNot(ignored contains _.key))
    }

    def drop[T <: ModuleBase](ignored: DIKey)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.filterNot(_.key == ignored))
    }

    def overriddenBy[T <: ModuleBase](that: ModuleBase)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(overrideImpl(module.iterator, that.iterator).toSet)
    }

    def tagged[T <: ModuleBase](tags: BindingTag*)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.map(_.addTags(tags.toSet)))
    }

    def removeTags[T <: ModuleBase](tags: BindingTag*)(implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.map(b => b.withTags(b.tags -- tags)))
    }

    def untagged[T <: ModuleBase](implicit T: ModuleMake.Aux[S, T]): T = {
      T.make(module.bindings.map(_.withTags(Set.empty)))
    }
  }

  private[distage] def overrideImpl(existingIterator: Iterator[Binding], overridingIterator: Iterator[Binding]): Iterator[Binding] = {
    if (overridingIterator.isEmpty) return existingIterator
    if (existingIterator.isEmpty) return overridingIterator

    val existingIndex = existingIterator.map(b => (b.key, b.isMutator) -> b).toMultimapMut
    val newIndex = overridingIterator.map(b => (b.key, b.isMutator) -> b).toMultimapMut
    val mergedKeys = existingIndex.keySet ++ newIndex.keySet

    mergedKeys.iterator.flatMap {
      case k @ (_, false) => newIndex.getOrElse(k, existingIndex.getOrElse(k, Set.empty))
      case k @ (_, true) => newIndex.getOrElse(k, Set.empty).iterator ++ existingIndex.getOrElse(k, Set.empty).iterator
    }
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def optionalCatsPartialOrderHashForModuleBase[T <: ModuleBase, K[_]: `cats.kernel.PartialOrder with cats.kernel.Hash`]: K[T] = {
    import cats.instances.set.*
    new PartialOrder[T] with Hash[T] {
      override def partialCompare(x: T, y: T): Double = PartialOrder[Set[Binding]].partialCompare(x.bindings, y.bindings)
      override def hash(x: T): Int = x.hashCode
      override def eqv(x: T, y: T): Boolean = x == y
    }.asInstanceOf[K[T]]
  }

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def optionalCatsBoundedSemilatticeForModuleBase[T <: ModuleBase, K[_]](implicit T: ModuleMake[T], @unused K: `cats.kernel.BoundedSemilattice`[K]): K[T] =
    new BoundedSemilattice[T] {
      def empty: T = T.empty
      def combine(x: T, y: T): T = (x ++ y)(T)
    }.asInstanceOf[K[T]]

}

private[definition] sealed trait ModuleBaseLowPriorityInstances {

  // emulate bivariance for ModuleMake. The only purpose of the first parameter is to initiate
  // the search in its companion object, otherwise the parameter should be ignored when deciding
  // whether instances are subtypes of each other (aka bivariance)
  @inline implicit final def makeSelf[T <: ModuleBase](implicit T: ModuleMake.Aux[Nothing, T]): ModuleMake[T] =
    T.asInstanceOf[ModuleMake[T]]

}
