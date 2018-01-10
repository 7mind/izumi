package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.PullDependency
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp._
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningOp._
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningConflict._
import org.bitbucket.pshirshov.izumi.di.model.plan._

sealed trait Provisioning

object Provisioning {
  case class Possible(ops: Seq[ExecutableOp]) extends Provisioning
  case class Impossible(implDef: ImplDef) extends Provisioning
}

class BasicInjector
  extends Injector
    with WithSanityChecks
    with WithReflection {

  // todo: provide from outside
  protected def resolver: PlanResolver = new DefaultPlanResolver()

  override def plan(context: DIDef): ReadyPlan = {
    System.err.println(s"Planning on context $context")

    val metaPlan = context.bindings.foldLeft(DodgyPlan(Seq.empty[DodgyOp])) {
      case (currentPlan, definition) =>
        val knowsTargets = currentPlan.steps.map(_.op.target).toSet
        val deps = enumerateDeps(definition.implementation)
        val (resolved, unresolved) = deps.partition(d => knowsTargets.contains(d.wireWith))
        // we don't need resolved deps, we already have them in plan

        val toPull = unresolved.map(dep => ExecutableOp.PullDependency(dep.wireWith))
        provisioning(definition.target, definition.implementation, deps) match {
          case Provisioning.Possible(toProvision) =>
            System.err.println(s"toPull = $toPull, toProvision=$toProvision")

            assertSanity(toPull ++ toProvision)
            val nextPlan = extendPlan(currentPlan, toPull, toProvision)

            val next = DodgyPlan(nextPlan)
            System.err.println("-" * 60 + " Next Plan " + "-" * 60)
            System.err.println(next)
            next

          case Provisioning.Impossible(implDef) =>
            val next = DodgyPlan(currentPlan.steps :+ UnbindableBinding(definition))
            System.err.println("-" * 60 + " Next Plan (failed) " + "-" * 60)
            System.err.println(next)
            next
        }

    }


    val plan = resolver.resolve(metaPlan)
    assertSanity(plan)
    plan
  }


  private def provisioning(target: DIKey, impl: ImplDef, deps: Seq[Association]): Provisioning = {
    import Provisioning._
    impl match {
      case ImplDef.TypeImpl(symb) if isConcrete(symb) =>
        Possible(Seq(ExecutableOp.InstantiateClass(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if isWireableAbstract(symb) =>
        Possible(Seq(ExecutableOp.InstantiateTrait(target, symb, deps)))

      case ImplDef.InstanceImpl(instance) =>
        Possible(Seq(ExecutableOp.ReferenceInstance(target, instance)))

      case other =>
        Impossible(other)
    }
  }

  private def extendPlan(currentPlan: DodgyPlan, toPull: Seq[ExecutableOp.PullDependency], toProvision: Seq[ExecutableOp]) = {
    val withConflicts = findConflicts(currentPlan, toProvision)

    val (before, after) = splitCurrentPlan(currentPlan, withConflicts)

    val transformations = withConflicts.map {
      case NoConflict(newOp) =>
        Put(newOp)

      // TODO: here we may check for circular deps
      case Conflict(newOp, Statement(existing: PullDependency)) =>
        Replace(existing, newOp)

      case Conflict(newOp, existing) if Statement(newOp) == existing =>
        SolveRedefinition(newOp)

      case Conflict(newOp, existing) =>
        SolveUnsolvable(newOp, existing.op)
    }

    val replacements = transformations.collect { case t: Replace => Statement(t.op): DodgyOp }.toSet

    val beforeReplaced = before.filterNot(replacements.contains)
    val afterReplaced = after.filterNot(replacements.contains)

    // TODO: separate ADT for this case?..

    val transformationsWithoutReplacements = transformations.map {
      case t: Replace => DodgyOp.Statement(t.replacement)
      case t: Put => DodgyOp.Statement(t.op)
      case t: SolveRedefinition => DodgyOp.DuplicatedStatement(t.op)
      case t: SolveUnsolvable => DodgyOp.UnsolvableConflict(t.op, t.existing)
    }

    toPull.map(DodgyOp.Statement) ++ beforeReplaced ++
      transformationsWithoutReplacements ++
      afterReplaced

  }

  private def splitCurrentPlan(currentPlan: DodgyPlan, withConflicts: Seq[PlanningConflict]): (Seq[DodgyOp], Seq[DodgyOp]) = {
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

  private def findConflicts(currentPlan: DodgyPlan, toProvision: Seq[ExecutableOp]): Seq[PlanningConflict] = {
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

  private def enumerateDeps(impl: ImplDef): Seq[Association] = {
    impl match {
      case i: ImplDef.TypeImpl =>
        symbolDeps(i.impl)
      case i: ImplDef.InstanceImpl =>
        Seq()
    }
  }

  override def produce(dIPlan: ReadyPlan): DIContext = ???
}


