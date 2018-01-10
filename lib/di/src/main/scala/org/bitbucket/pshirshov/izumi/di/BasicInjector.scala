package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.PlanTransformation._
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanMetaStep._
import org.bitbucket.pshirshov.izumi.di.definition.DIDef
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.Op.PullDependency
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningConflict._
import org.bitbucket.pshirshov.izumi.di.model.plan._


case class PlanSteps(steps: Seq[PlanMetaStep]) {
  override def toString: String = steps.collect {
    case Statement(op) =>
      op.format
    case v => v.toString
  }.mkString("\n")
}


class BasicInjector extends Injector {
  override def plan(context: DIDef): DIPlan = {
    val metaPlan = context.bindings.foldLeft(PlanSteps(Seq.empty[PlanMetaStep])) {
      case (currentPlan, definition) =>
        if (!definition.implementation.asClass.baseClasses.contains(definition.target.symbol.asClass)) {
          throw new IllegalStateException(s"Cannot bind unbindable: $definition") // TODO: specific exception
        }

        val knowsTargets = currentPlan.steps.map(_.op.target).toSet
        val deps = allDeps(definition.implementation)
        val (resolved, unresolved) = deps.partition(d => knowsTargets.contains(d.wireWith))
        // we don't need resolved deps, we already have them in plan

        val toPull = unresolved.map(dep => Op.PullDependency(dep.wireWith))
        val toProvision = provisioning(definition.target, definition.implementation, deps)

        assertSanity(toPull)
        assertSanity(toProvision)

        val nextPlan = extendPlan(currentPlan, toPull, toProvision)
        val next = PlanSteps(nextPlan)
        System.err.println("-" * 60 + " Next Plan " + "-" * 60)
        System.err.println(next)
        next
    }

    val plan = new ImmutablePlan(metaPlan.steps.collect { case Statement(op) => op })

    System.err.println("=" * 60 + " Final Plan " + "=" * 60)
    System.err.println(s"$plan")
    System.err.println("\n")

    // TODO: make sure circular deps are gone
    assertSanity(plan.getPlan)

    plan

  }


  private def extendPlan(currentPlan: PlanSteps, toPull: Seq[Op.PullDependency], toProvision: Seq[Op]) = {
    val withConflicts = findConflicts(currentPlan, toProvision)

    val (before, after) = splitCurrentPlan(currentPlan, withConflicts)

    val transformations = withConflicts.map {
      case NoConflict(newOp) =>
        Put(newOp)

      // TODO: here we may check for circular deps
      case Conflict(newOp, Statement(existing: PullDependency)) =>
        Replace(existing, newOp)

      case Conflict(newOp, existing) if newOp == existing =>
        SolveRedefinition(newOp)

      case Conflict(newOp, existing) =>
        SolveUnsolvable(newOp, existing.op)
    }

    val replacements = transformations.collect { case t: Replace => Statement(t.op): PlanMetaStep }.toSet

    val beforeReplaced = before.filterNot(replacements.contains)
    val afterReplaced = after.filterNot(replacements.contains)

    // TODO: separate ADT for this case?..

    val transformationsWithoutReplacements = transformations.map {
      case t: Replace => PlanMetaStep.Statement(t.replacement)
      case t: Put => PlanMetaStep.Statement(t.op)
      case t: SolveRedefinition => PlanMetaStep.Duplicate(t.op)
      case t: SolveUnsolvable => PlanMetaStep.ConflictingStatement(t.op, t.existing)
    }

    toPull.map(PlanMetaStep.Statement) ++ beforeReplaced ++
      transformationsWithoutReplacements ++
      afterReplaced

  }

  private def splitCurrentPlan(currentPlan: PlanSteps, withConflicts: Seq[PlanningConflict]): (Seq[PlanMetaStep], Seq[PlanMetaStep]) = {
    import PlanningConflict._
    val currentPlanBlock = currentPlan.steps
    val insertAt = withConflicts
      .map {
        case Conflict(_, existingOp) =>
          currentPlanBlock.indexOf(Put(existingOp.op))

        case _: NoConflict =>
          currentPlanBlock.length
      }
      .min

    val (before, after) = currentPlanBlock.splitAt(insertAt)
    (before, after)
  }

  private def findConflicts(currentPlan: PlanSteps, toProvision: Seq[Op]): Seq[PlanningConflict] = {
    import PlanningConflict._
    val currentUnwrapped = currentPlan.steps.toSet

    val withConflicts = toProvision.map {
      newOp =>
        // safe to use .find, plan itself cannot contain conflicts
        // TODO: ineffective!
        currentUnwrapped.find(_.op.target == newOp.target) match {
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

  private def isConcrete(Symb: Symb) = {
    Symb.isClass && !Symb.isAbstract
  }

  override def produce(dIPlan: DIPlan): DIContext = ???
}
