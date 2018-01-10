package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.plan.Op.PullDependency
import org.bitbucket.pshirshov.izumi.di.plan._


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

    context.bindings.foldLeft(DIPlan.empty) {
      case (currentPlan, definition) =>
        if (!definition.implementation.asClass.baseClasses.contains(definition.target.symbol.asClass)) {
          throw new IllegalStateException(s"Cannot bind unbindable: $definition") // TODO: specific exception
        }

        val deps = allDeps(definition.implementation)
        val (resolved, unresolved) = deps.partition(d => currentPlan.contains(d.wireWith))
        // we don't need resolved deps, we already have them in plan

        val toPull = unresolved.map(dep => Op.PullDependency(dep.wireWith): Op)
        val toProvision = provisioning(definition.target, definition.implementation, deps)

        assertSanity(toPull)
        assertSanity(toProvision)

        val nextPlan = extendPlan(currentPlan, toPull, toProvision)
        val next = new ImmutablePlan(nextPlan)

        System.err.println("-" * 120)
        System.err.println(s"Next plan:\n${next}")
        assertSanity(nextPlan)

        next
    }

  }

  private def extendPlan(currentPlan: DIPlan, toPull: Seq[Op], toProvision: Seq[Op]) = {
    val nextPlan = scala.collection.mutable.ArrayBuffer[Op](currentPlan.getPlan: _*)
    val withConflicts = toProvision.map {
      newOp =>
        // safe to use .find, plan cannot contain conflicts
        // TODO: ineffective!
        Conflict(newOp, nextPlan.find(_.target == newOp.target))
    }

    val allIndexes = withConflicts.flatMap(_.existingOps)
      .map(nextPlan.indexOf) :+ nextPlan.length

    val minIndex = allIndexes.min
    nextPlan.insertAll(minIndex, toProvision)

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

    toPull ++ nextPlan
  }

  private def provisioning(target: DIKey, impl: Symb, deps: Seq[Association]): Seq[Op] = {
    val provideOp = if (isConcrete(impl)) {
      Op.InstantiateClass(target, impl, deps)
    } else {
      Op.InstantiateTrait(target, impl, deps)
    }
    Seq(provideOp)
  }


  private def allDeps(Symb: Symb): Seq[Association] = {
    if (isConcrete(Symb)) {
      val constructors = Symb.info.decls.filter(_.isConstructor)
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
      val declaredAbstractMethods = Symb.info.decls.filter(d => d.isMethod && d.isAbstract && !d.isSynthetic && d.info.paramLists.isEmpty)

      // TODO: here we should handle annotations/etc
      declaredAbstractMethods.map(m => Association.Method(m, DIKey.TypeKey(m.info.resultType.typeSymbol))).toSeq
    }
  }


  // TODO: quadratic
  private def duplicates(keys: Seq[DIKey]): Seq[DIKey] = keys.map {
    k => (k, keys.count(_ == k))
  }.filter(_._2 > 1).map(_._1)

  private def assertSanity(ops: Seq[Op]): Unit = {
    assertKeysSanity(ops.map(_.target))

  }

  private def assertKeysSanity(keys: Seq[DIKey]): Unit = {
    if (duplicates(keys).nonEmpty) {
      throw new IllegalArgumentException(s"Duplicate keys: $keys!")
    }
  }

  private def isSane(keys: Seq[DIKey]): Boolean = keys.lengthCompare(keys.distinct.size) == 0

  private def isConcrete(Symb: Symb) = {
    Symb.isClass && !Symb.isAbstract
  }

  override def produce(dIPlan: DIPlan): DIContext = ???
}