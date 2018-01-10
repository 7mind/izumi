package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.PlanTransformation.{Duplicated, Leave, Replace, UnsolvableConflict}
import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.plan.Op.PullDependency
import org.bitbucket.pshirshov.izumi.di.plan.{Association, DIPlan, ImmutablePlan, Op}

trait PlanningConflict

object PlanningConflict {

  case class NoConflict(newOp: Op) extends PlanningConflict

  case class Conflict(newOp: Op, existingOp: Op) extends PlanningConflict

}

class BasicInjector extends Injector {
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


        val next = new ImmutablePlan(nextPlan.collect { case Leave(op) => op })

        System.err.println("-" * 120)
        System.err.println(s"> ${nextPlan}")
        System.err.println(s"Next plan:\n${next}")
        assertSanity(next.getPlan)
        next
    }
  }


  private def extendPlan(currentPlan: DIPlan, toPull: Seq[Op], toProvision: Seq[Op]) = {
    import PlanningConflict._
    val withConflicts = findConflicts(currentPlan, toProvision)

    val currentPlanBlock = currentPlan.getPlan.map(Leave)
    val insertAt  = withConflicts
      .map {
        case Conflict(_, existingOp) =>
          currentPlanBlock.indexOf(Leave(existingOp))

        case _: NoConflict =>
          currentPlanBlock.length
      }
      .min

    val (before, after) = currentPlanBlock.splitAt(insertAt)

    val transformations = withConflicts.map {
      case NoConflict(newOp) =>
        Leave(newOp)

        // TODO: here we may check for circular deps
      case Conflict(newOp, existing: PullDependency) =>
        Replace(existing, newOp)

      case Conflict(newOp, existing) if newOp == existing =>
        Duplicated(newOp)

      case Conflict(newOp, existing) =>
        UnsolvableConflict(newOp, existing)
    }

    val replacements = transformations.collect { case t: Replace => Leave(t.op) }.toSet

    val beforeReplaced = before.filterNot(replacements.contains)
    val afterReplaced = after.filterNot(replacements.contains)
    val transformationsWithoutReplacements = transformations.map {
      case t: Replace => Leave(t.replacement)
      case v => v
    }

    toPull.map(Leave) ++
      beforeReplaced ++
      transformationsWithoutReplacements ++
      afterReplaced
  }

  private def findConflicts(currentPlan: DIPlan, toProvision: Seq[Op]): Seq[PlanningConflict] = {
    import PlanningConflict._
    val currentUnwrapped = currentPlan.getPlan.toSet

    val withConflicts = toProvision.map {
      newOp =>
        // safe to use .find, plan cannot contain conflicts
        // TODO: ineffective!
        currentUnwrapped.find(_.target == newOp.target) match {
          case Some(existing) =>
            Conflict(newOp, existing)
          case _ =>
            NoConflict(newOp)
        }
    }
    withConflicts
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
