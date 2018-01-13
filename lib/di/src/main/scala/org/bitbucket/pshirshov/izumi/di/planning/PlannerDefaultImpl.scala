package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ContextDefinition, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp._
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{AddToSet, CreateSet, ImportDependency, InstantiationOp}
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningConflict.{Conflict, NoConflict}
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningOp.{Put, SolveRedefinition, SolveUnsolvable}
import org.bitbucket.pshirshov.izumi.di.model.plan.Provisioning.{Impossible, Possible}
import org.bitbucket.pshirshov.izumi.di.model.plan._
import org.bitbucket.pshirshov.izumi.di.reflection.ReflectionProvider
import org.bitbucket.pshirshov.izumi.di.{Planner, TypeFull}


case class Value[A](value: A) {
  @inline final def map[B](f: A => B): Value[B] =
    Value(f(this.value))
}

case class NextOps(
                    imports: Set[ImportDependency]
                    , sets: Set[CreateSet]
                    , provisions: Seq[InstantiationOp]
                  ) {
  def flatten: Seq[ExecutableOp] = {
    imports.toSeq ++ sets.toSeq ++ provisions
  }
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

  override def plan(context: ContextDefinition): FinalPlan = {
    val plan = context.bindings.foldLeft(DodgyPlan.empty) {
      case (currentPlan, binding) =>
        val nextOps = computeProvisioning(currentPlan, binding)

        nextOps match {
          case Possible(ops) =>
            sanityChecker.assertNoDuplicateOps(ops.flatten)
            val next = extendPlan(currentPlan, binding, ops)
            sanityChecker.assertNoDuplicateOps(next.statements)
            next

          case Impossible(implDefs) =>
            val next = DodgyPlan(
              currentPlan.imports
              , currentPlan.sets
              , currentPlan.steps :+ UnbindableBinding(binding, implDefs)
            )
            next
        }
    }

    val finalPlan = Value(plan)
      .map(forwardingRefResolver.resolve)
      .map(planResolver.resolve(_, context))
      .value

    sanityChecker.assertSanity(finalPlan)

    finalPlan
  }

  private def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): Provisioning[NextOps, Seq[ImplDef]] = {
    binding match {
      case c: SingletonBinding =>
        val deps = enumerateDeps(c)
        val toImport = computeImports(currentPlan, deps)
        val toProvision = provisioning(c, deps)
        toProvision
          .map(newOps => NextOps(toImport, Set.empty, newOps))

      case s: SetBinding =>
        val target = s.target
        val elementKey = DIKey.SetElementKey(target, getSymbol(s.implementation))

        computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation)) match {
          case Possible(NextOps(imports, sets, ops)) =>
            Possible(NextOps(
              imports
              , sets + ExecutableOp.CreateSet(target, target.symbol)
              , ops :+ ExecutableOp.AddToSet(target, elementKey)
            ))

          case Impossible(broken) =>
            // TODO: some data is lost here, we need to stack it
            Impossible(broken)
          //NextOps(imports, Impossible(s.implementation))
        }

      case s: EmptySetBinding =>
        Possible(NextOps(Set.empty, Set(ExecutableOp.CreateSet(s.target, s.target.symbol)), Seq.empty))

    }
  }

  private def computeImports(currentPlan: DodgyPlan, deps: Seq[Association]): Set[ImportDependency] = {
    val knownTargets = currentPlan.flatten.flatMap {
      case Statement(op) =>
        Seq(op.target)
      case _ =>
        Seq()
    }.toSet
    val (resolved@_, unresolved) = deps.partition(d => knownTargets.contains(d.wireWith))
    // we don't need resolved deps, we already have them in finalPlan
    val toImport = unresolved.map(dep => ExecutableOp.ImportDependency(dep.wireWith))
    toImport.toSet
  }

  private def provisioning(binding: SingletonBinding, deps: Seq[Association]): Provisioning[Seq[InstantiationOp], Seq[ImplDef]] = {
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
        Impossible(Seq(other))
    }
  }

  private def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    val newProvisions = currentOp
      .provisions
      .map(op => Statement(ExecutableOp.ImportDependency(op.target))).toSet


    val withConflicts = findConflicts(currentPlan.steps, currentOp.provisions)

    val (before, after) = splitCurrentPlan(currentPlan.steps, withConflicts)

    val transformations = withConflicts.map {
      case NoConflict(newOp) =>
        Put(newOp)

      case Conflict(newOp, existing) if Statement(newOp) == existing =>
        SolveRedefinition(newOp)

      case Conflict(newOp, existing) =>
        SolveUnsolvable(newOp, existing)
    }

    val transformationsWithoutReplacements = transformations.map {
      case t: Put => DodgyOp.Statement(t.op)
      case t: SolveRedefinition => DodgyOp.DuplicatedStatement(t.op)
      case t: SolveUnsolvable => DodgyOp.UnsolvableConflict(t.op, t.existing)
    }

    val safeNewProvisions: Seq[DodgyOp] = Seq(Nop(s"// head: $binding")) ++
      before ++
      Seq(Nop(s"// tran: $binding")) ++
      transformationsWithoutReplacements ++
      Seq(Nop(s"// tail: $binding")) ++
      after ++
      Seq(Nop(s"//  end: $binding"))

    val newImports = (currentPlan.imports ++ currentOp.imports.map(Statement)) -- newProvisions
    val newSets = currentPlan.sets ++ currentOp.sets.map(Statement)

    DodgyPlan(
      newImports
      , newSets
      , safeNewProvisions
    )
  }

  private def splitCurrentPlan(currentPlan: Seq[DodgyOp], withConflicts: Seq[PlanningConflict]): (Seq[DodgyOp], Seq[DodgyOp]) = {
    import PlanningConflict._
    val currentPlanBlock = currentPlan
    val insertAt = Seq(Seq(currentPlanBlock.length), withConflicts
      .map {
        case Conflict(_, Statement(op)) =>
          currentPlanBlock.indexOf(op)

        case _ => // we don't consider all other cases
          currentPlanBlock.length
      })
      .flatten
      .min

    val (before, after) = currentPlanBlock.splitAt(insertAt)
    (before, after)
  }

  private def findConflicts(currentPlan: Seq[DodgyOp], toProvision: Seq[ExecutableOp]): Seq[PlanningConflict] = {
    import PlanningConflict._
    val currentUnwrapped = currentPlan.collect { case s: Statement => s }.toSet

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
      case (eop: AddToSet, nop: AddToSet) if eop.target == nop.target =>
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
