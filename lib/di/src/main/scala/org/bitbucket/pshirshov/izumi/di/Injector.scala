package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.Def.SingletonBinding
import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.plan.Op.PullDependency
import org.bitbucket.pshirshov.izumi.di.plan._

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._


trait DIContext {
  def instance[T: Tag](key: DIKey): T

  def subInjector(): Injector
}

trait Injector {
  def plan(context: DIDef): DIPlan

  def produce(dIPlan: DIPlan): DIContext
}

class BasicInjector extends Injector {
  case class Conflict(newOp: Op, existingOps: Option[Op])
  case class JustConflict(newOp: Op, existingOps: Op)

  override def plan(context: DIDef): DIPlan = {

    context.bindings.map(withKey).foldLeft(DIPlan.empty) {
      case (currentPlan, WithKey(definition, key)) =>
        val nextPlan = scala.collection.mutable.ArrayBuffer[Op](currentPlan.getPlan :_*)

        val deps = allDeps(key.symbol) // TODO: we need to be sure that toProvision is sane

        val (resolved, unresolved) = deps.partition(d => currentPlan.contains(d.wireWith))
        // we don't need resolved deps, we already have them in plan

        val toPull = unresolved.map(dep => Op.PullDependency(dep.wireWith): Op)

        val toProvision = provisioning(key, deps) // TODO: we need to be sure that toProvision is sane

        val withConflicts = toProvision.map {
          newOp =>
            // plan cannot contain conflicts
            Conflict(newOp, nextPlan.find(_.target == newOp.target)) // TODO: ineffective!
        }

        val allIndexes = withConflicts.flatMap(_.existingOps)
          .map(nextPlan.indexOf) :+ nextPlan.length

        val minIndex = allIndexes.min

        nextPlan.insertAll(minIndex, toProvision)
        nextPlan.insertAll(0, toPull)

        val justConflicts = withConflicts.collect {
          case Conflict(newOp, Some(existingOp)) =>
            JustConflict(newOp, existingOp)
        }

        justConflicts.foreach {
          case JustConflict(newOp, existing) if newOp == existing =>
            System.err.println(s"Already have it: $newOp == $existing")
            ???

          case JustConflict(newOp, existing: PullDependency) =>
            nextPlan -= existing

          case unsolvable =>
            System.err.println(s"Unsolvable: $unsolvable")
            ???
        }

        if (!isSane(nextPlan.map(_.target))) {
          throw new IllegalStateException(s"Duplicate keys in next plan: $nextPlan")
        }
        
        val next = new ImmutablePlan(nextPlan)

        System.err.println("-"* 120)
        System.err.println(s"Next plan:\n${next}")
        
        next
    }

  }

  private def provisioning(key: DIKey, deps: Seq[Association]): Seq[Op] = {
    val provideOp = if (isConcrete(key.symbol)) {
      Op.InstantiateClass(key, deps)
    } else {
      Op.InstantiateTrait(key, deps)
    }
    Seq(provideOp)
  }

  case class WithKey(d: Def, dIKey: DIKey)

  private def withKey(d: Def): WithKey = {
    val key = d match {
      case b: SingletonBinding[_, _] =>
        DIKey.TypeKey(b.bindingType.tpe.typeSymbol)
    }
    WithKey(d, key)
  }

  private def allDeps(symbol: universe.Symbol): Seq[Association] = {
    if (isConcrete(symbol)) {
      val constructors = symbol.info.decls.filter(_.isConstructor)
      // TODO: list should not be empty (?) and should has only one element (?)
      val selectedConstructor = constructors.head

      val paramLists = selectedConstructor.info.paramLists
      // TODO: param list should not be empty (?), what to do with multiple lists?..
      val selectedParamList = paramLists.head

      selectedParamList.map {
        parameter =>
          // TODO: here we should handle annotations/etc
          Association.Parameter(parameter, DIKey.TypeKey(parameter.info.typeSymbol))
      }
    } else {
      // empty paramLists means parameterless method, List(List()) means nullarg method()
      val declaredAbstractMethods = symbol.info.decls.filter(d => d.isMethod && d.isAbstract && !d.isSynthetic && d.info.paramLists.isEmpty)

      // TODO: here we should handle annotations/etc
      declaredAbstractMethods.map(m => Association.Method(m, DIKey.TypeKey(m.info.resultType.typeSymbol))).toSeq
    }
  }

  private def isSane(keys: Seq[DIKey]): Boolean = keys.lengthCompare(keys.distinct.size) == 0

  private def isConcrete(symbol: Symbol) = {
    symbol.isClass && !symbol.isAbstract
  }

  override def produce(dIPlan: DIPlan): DIContext = ???
}