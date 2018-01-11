package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp._
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.ImportDependency
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningConflict._
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningOp._
import org.bitbucket.pshirshov.izumi.di.model.plan._


/**
TODO:
- circulars: outside of resolver

- sanity checks/partial order/nulls
- strategies as parent values

+ extension point: custom op
+ factories: filtei parameters out of products
*/


class BasicInjector
  extends Injector
    with WithSanityChecks
    with WithReflection {

  // todo: provide from outside
  protected def planResolver: PlanResolver = new PlanResolverDefaultImpl()
  protected def forwardingRefResolver: ForwardingRefResolver = new ForwardingRefResolverDefaultImpl()

  case class CurrentOp(definition: Def, toImport: Seq[ExecutableOp.ImportDependency], toProvision: Seq[ExecutableOp])

  override def plan(context: DIDef): ReadyPlan = {
//    System.err.println(s"Planning on context $context")

    val plan = context.bindings.foldLeft(DodgyPlan(Seq.empty[DodgyOp])) {
      case (currentPlan, definition) =>
        val knowsTargets = currentPlan.steps.flatMap {
          case Statement(op) =>
            Seq(op.target)
          case _ =>
            Seq()
        }.toSet

        val deps = enumerateDeps(definition.implementation)
        val (resolved, unresolved) = deps.partition(d => knowsTargets.contains(d.wireWith))
        // we don't need resolved deps, we already have them in finalPlan

        val toImport = unresolved.map(dep => ExecutableOp.ImportDependency(dep.wireWith))
        provisioning(definition.target, definition.implementation, deps) match {
          case Provisioning.Possible(toProvision) =>
//            System.err.println(s"toImport = $toImport, toProvision=$toProvision")

            assertSanity(toImport ++ toProvision)
            val nextPlan = extendPlan(currentPlan, CurrentOp(definition, toImport, toProvision))

            val next = DodgyPlan(nextPlan)
//            System.err.println("-" * 60 + " Next Plan " + "-" * 60)
//            System.err.println(next)
            next

          case Provisioning.Impossible(implDef) =>
            val next = DodgyPlan(currentPlan.steps :+ UnbindableBinding(definition))
//            System.err.println("-" * 60 + " Next Plan (failed) " + "-" * 60)
//            System.err.println(next)
            next
        }
    }

    def withoutForwardingRefs = forwardingRefResolver.resolve(plan)
    val finalPlan = planResolver.resolve(withoutForwardingRefs)
    assertSanity(finalPlan)
    finalPlan
  }


  private def provisioning(target: DIKey, impl: ImplDef, deps: Seq[Association]): Provisioning = {
    import Provisioning._
    import WithReflection._
    impl match {
      case ImplDef.TypeImpl(symb) if isConcrete(symb) =>
        Possible(Seq(ExecutableOp.InstantiateClass(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if isWireableAbstract(symb) =>
        Possible(Seq(ExecutableOp.InstantiateTrait(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if isFactory(symb) =>
        Possible(Seq(ExecutableOp.InstantiateFactory(target, symb, deps)))

      case ImplDef.InstanceImpl(instance) =>
        Possible(Seq(ExecutableOp.ReferenceInstance(target, instance)))

      case ImplDef.CustomImpl(instance) =>
        Possible(Seq(ExecutableOp.CustomOp(target, instance)))

      case other =>
        Impossible(other)
    }
  }

  private def extendPlan(currentPlan: DodgyPlan, currentOp: CurrentOp) = {
    import currentOp._
    val withConflicts = findConflicts(currentPlan, toProvision)

    val (before, after) = splitCurrentPlan(currentPlan, withConflicts)

    val transformations = withConflicts.map {
      case NoConflict(newOp) =>
        Put(newOp)

      // TODO: here we may check for circular deps
      case Conflict(newOp, Statement(existing: ImportDependency)) =>
        Replace(existing, newOp)

      case Conflict(newOp, existing) if Statement(newOp) == existing =>
        SolveRedefinition(newOp)

      case Conflict(newOp, existing) =>
        SolveUnsolvable(newOp, existing)
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
    Seq(Nop(s"//  imp: ${currentOp.definition}")) ++
      toImport.map(DodgyOp.Statement) ++
      Seq(Nop(s"// head: ${currentOp.definition}")) ++
      beforeReplaced ++
      Seq(Nop(s"// tran: ${currentOp.definition}")) ++
      transformationsWithoutReplacements ++
      Seq(Nop(s"// tail: ${currentOp.definition}")) ++
      afterReplaced ++
      Seq(Nop(s"//  end: ${currentOp.definition}"))
  }

  private def splitCurrentPlan(currentPlan: DodgyPlan, withConflicts: Seq[PlanningConflict]): (Seq[DodgyOp], Seq[DodgyOp]) = {
    import PlanningConflict._
    val currentPlanBlock = currentPlan.steps
    val insertAt = withConflicts
      .map {
        case Conflict(_, Statement(op)) =>
          currentPlanBlock.indexOf(op)

        case _ => // we don't consider all other cases
          currentPlanBlock.length
      }
      .min

    val (before, after) = currentPlanBlock.splitAt(insertAt)
    (before, after)
  }

  private def findConflicts(currentPlan: DodgyPlan, toProvision: Seq[ExecutableOp]): Seq[PlanningConflict] = {
    import PlanningConflict._
    val currentUnwrapped = currentPlan.steps.collect { case s: Statement => s }.toSet

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


