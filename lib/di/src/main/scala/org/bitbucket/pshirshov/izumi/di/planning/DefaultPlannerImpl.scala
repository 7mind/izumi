package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.definition.{ContextDefinition, Binding, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp._
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{ImportDependency, SetOp}
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningConflict._
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningOp._
import org.bitbucket.pshirshov.izumi.di.model.plan.Provisioning.{Impossible, Possible}
import org.bitbucket.pshirshov.izumi.di.model.plan._
import org.bitbucket.pshirshov.izumi.di.reflection.ReflectionProvider
import org.bitbucket.pshirshov.izumi.di.{Planner, TypeFull}


case class Value[A](value: A) {
  @inline final def map[B](f: A => B): Value[B] =
    Value(f(this.value))
}

class DefaultPlannerImpl
(
  protected val planResolver: PlanResolver
  , protected val forwardingRefResolver: ForwardingRefResolver
  , protected val reflectionProvider: ReflectionProvider
  , protected val sanityChecker: SanityChecker
  , protected val customOpHandler: CustomOpHandler
)
  extends Planner {

  case class CurrentOp(definition: Binding, toImport: Seq[ExecutableOp.ImportDependency], toProvision: Seq[ExecutableOp])
  
  case class NextOps(toImport: Seq[ImportDependency], toProvision: Provisioning)

  override def plan(context: ContextDefinition): FinalPlan = {
    //    System.err.println(s"Planning on context $context")

    val plan = context.bindings.foldLeft(DodgyPlan(Seq.empty[DodgyOp])) {
      case (currentPlan, definition) =>
        val nextOps = computeProvisioning(currentPlan, definition)

        nextOps match {
          case NextOps(toImport, Provisioning.Possible(possible)) =>
            //            System.err.println(s"toImport = $toImport, toProvision=$toProvision")

            sanityChecker.assertNoDuplicateOps(toImport ++ possible)
            val nextPlan = extendPlan(currentPlan, CurrentOp(definition, toImport, possible))

            val next = DodgyPlan(nextPlan)
//            System.err.println("-" * 60 + " Next Plan " + "-" * 60)
//            System.err.println(next)
            next

          case NextOps(_, Provisioning.Impossible(_)) =>
            val next = DodgyPlan(currentPlan.steps :+ UnbindableBinding(definition))
//            System.err.println("-" * 60 + " Next Plan (failed) " + "-" * 60)
//            System.err.println(next)
            next
        }
    }

    val (justOps, setDefinitions) = plan.steps.foldLeft((scala.collection.mutable.ArrayBuffer[DodgyOp](), scala.collection.mutable.HashSet[Statement]())) {
      case ((ops, createSetOps), s@Statement(_:ExecutableOp.CreateSet)) =>
        (ops, createSetOps += s)

      case ((ops, createSetOps), op) =>
        (ops += op, createSetOps)
    }

    val withSetsAhead = DodgyPlan(setDefinitions.toSeq ++ justOps)

    val finalPlan = Value(withSetsAhead)
      .map(forwardingRefResolver.resolve)
      .map(planResolver.resolve(_, context))
      .value

    sanityChecker.assertSanity(finalPlan)
    
    finalPlan
  }

  private def computeProvisioning(currentPlan: DodgyPlan, definition: Binding): NextOps = {
    definition match {
      case c: SingletonBinding =>
        val deps = enumerateDeps(c)
        val toImport = computeImports(currentPlan, deps)
        val toProvision = provisioning(c, deps)
        NextOps(toImport, toProvision)

      case s: SetBinding =>
        val target = s.target
        val elementKey = DIKey.SetElementKey(target, getSymbol(s.implementation))

        computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation)) match {
          case NextOps(imports, Possible(ops)) =>
            NextOps(imports, Possible(
              Seq(ExecutableOp.CreateSet(target, target.symbol)) ++
                ops ++
                Seq(ExecutableOp.AddToSet(target, elementKey))
            ))

          case NextOps(imports, other@_) =>
            // TODO: some data is lost here, we need to stack it
            NextOps(imports, Impossible(s.implementation))
        }

      case s: EmptySetBinding =>
        NextOps(Seq.empty, Possible(Seq(ExecutableOp.CreateSet(s.target, s.target.symbol))))

    }
  }

  private def computeImports(currentPlan: DodgyPlan, deps: Seq[Association]): Seq[ImportDependency] = {
    val knownTargets = currentPlan.steps.flatMap {
      case Statement(op) =>
        Seq(op.target)
      case _ =>
        Seq()
    }.toSet
    val (resolved@_, unresolved) = deps.partition(d => knownTargets.contains(d.wireWith))
    // we don't need resolved deps, we already have them in finalPlan
    val toImport = unresolved.map(dep => ExecutableOp.ImportDependency(dep.wireWith))
    toImport
  }

  private def provisioning(binding: SingletonBinding, deps: Seq[Association]): Provisioning = {
    import Provisioning._
    val target = binding.target

    binding.implementation match {
      case ImplDef.TypeImpl(symb) if reflectionProvider.isConcrete(symb) =>
        Possible(Seq(ExecutableOp.InstantiateClass(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if reflectionProvider.isWireableAbstract(symb) =>
        Possible(Seq(ExecutableOp.InstantiateTrait(target, symb, deps)))

      case ImplDef.TypeImpl(symb) if reflectionProvider.isFactory(symb) =>
        Possible(Seq(ExecutableOp.InstantiateFactory(target, symb, deps)))

      case ImplDef.InstanceImpl(symb, instance) =>
        Possible(Seq(ExecutableOp.ReferenceInstance(target, symb, instance)))

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
        currentUnwrapped.find(dop => areConflicting(newOp, dop.op)) match {
          case Some(existing) =>
            Conflict(newOp, existing)
          case _ =>
            NoConflict(newOp)
        }
    }
    withConflicts
  }

  def areConflicting(newOp: ExecutableOp, existingOp: ExecutableOp): Boolean = {
    (existingOp, newOp) match {
      case (eop: SetOp, nop: SetOp) if eop.target == nop.target =>
        false
      case (eop, nop) =>
        eop.target == nop.target
    }
  }


  private def enumerateDeps(definition: Binding): Seq[Association] = {
    definition match {
      case c: SingletonBinding =>
        enumerateDeps(c.implementation)
      case c: SetBinding =>
        enumerateDeps(c.implementation)
      case _ =>
        Seq()
    }
  }

  private def enumerateDeps(impl: ImplDef): Seq[Association] = {
    impl match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolDeps(i.impl)
      case _: ImplDef.InstanceImpl =>
        Seq()
      case c: ImplDef.CustomImpl =>
        customOpHandler.getDeps(c)
    }
  }

  private def getSymbol(impl: ImplDef): TypeFull = {
    impl match {
      case i: ImplDef.TypeImpl =>
        i.impl
      case i: ImplDef.InstanceImpl =>
        i.tpe
      case c: ImplDef.CustomImpl =>
        customOpHandler.getSymbol(c)
    }
  }
}


