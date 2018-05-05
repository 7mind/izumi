package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.fundamentals.collections.IzCollections

import scala.language.implicitConversions

trait ModuleDef {
  def bindings: Set[Binding]

  override def equals(obj: scala.Any): Boolean = obj match {
    case that: ModuleDef => bindings == that.bindings
    case _ => false
  }

  override def hashCode(): Int = bindings.hashCode()

  override def toString: String = bindings.toString()
}

final case class TrivialModuleDef(bindings: Set[Binding]) extends ModuleDef

object TrivialModuleDef extends ModuleDef {
  override def bindings: Set[Binding] = Set.empty

  def empty: TrivialModuleDef = TrivialModuleDef(Set.empty)
}

object ModuleDef {

  implicit final class ModuleDefSeqExt(private val defs: Seq[ModuleDef]) extends AnyVal {
    def merge(): ModuleDef = {
      defs.reduceLeftOption[ModuleDef](_ ++ _).getOrElse(TrivialModuleDef.empty)
    }
  }

  implicit final class ModuleDefCombine(private val moduleDef: ModuleDef) extends AnyVal {
    def ++(binding: Binding): ModuleDef = {
      TrivialModuleDef(moduleDef.bindings + binding)
    }

    def ++(that: ModuleDef): ModuleDef = {
      TrivialModuleDef(moduleDef.bindings ++ that.bindings)
    }

    def overridenBy(that: ModuleDef): ModuleDef = {
      // we replace existing items in-place and appending new at the end
      // set bindings should not be touched

      val existingSetElements = moduleDef.bindings.collect({case b: SetElementBinding[_] => b})
      val newSetElements = that.bindings.collect({case b: SetElementBinding[_] => b})
      val mergedSetElements = existingSetElements ++ newSetElements

      val existingSets = moduleDef.bindings.collect({case b: EmptySetBinding[_] => b})
      val newSets = that.bindings.collect({case b: EmptySetBinding[_] => b})
      val mergedSets = existingSets ++ newSets

      val mergedSetOperations = mergedSets ++ mergedSetElements

      val setOps = mergedSetOperations.map(_.key)

      val existingSingletons = moduleDef.bindings.collect({case b: SingletonBinding[_] => b})
      val newSingletons = that.bindings.collect({case b: SingletonBinding[_] => b})

      import IzCollections._
      val existingIndex = existingSingletons.map(b => b.key -> b).toMultimap
      val newIndex = newSingletons.map(b => b.key -> b).toMultimap
      val mergedKeys = existingIndex.keySet ++ newIndex.keySet

      val badKeys = setOps.intersect(mergedKeys)
      if (badKeys.nonEmpty) {
        throw new DIException(s"Cannot override bindings, unsolvable conflicts: $badKeys", null)
      }

      val mergedSingletons =  mergedKeys.flatMap {
        k =>
          val existingMappings = existingIndex.getOrElse(k, Set.empty)
          val newMappings = newIndex.getOrElse(k, Set.empty)

          if (existingMappings.isEmpty) {
            newMappings
          } else if (newMappings.isEmpty) {
            existingMappings
          } else {
            newMappings
          }
      }



      TrivialModuleDef(mergedSingletons ++ mergedSetOperations)
    }
  }

  // FIXME: remove BindingDSL
  implicit def moduleDefToBindingDSL(mod: ModuleDef): BindingDSL =
    new BindingDSL(mod.bindings)

}
