package izumi.distage.model.plan.impl

import izumi.distage.model.Locator
import izumi.distage.model.exceptions.{InvalidPlanException, MissingInstanceException}
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, ProxyOp, SemiplanOp}
import izumi.distage.model.plan.{Roots, OrderedPlan, SemiPlan}
import izumi.distage.model.reflection._
import izumi.distage.model.recursive.LocatorRef
import izumi.reflect.Tag

private[plan] trait OrderedPlanOps extends Any {
  this: OrderedPlan =>

  /** Print while omitting package names for unambiguous types */
  override final def toString: String = (this: OrderedPlan).render()

  /**
    * Check for any unresolved dependencies, if this
    * returns Right then the wiring is generally correct,
    * modulo runtime exceptions in constructors,
    * and `Injector.produce` should succeed.
    *
    * However, presence of imports does not *always* mean
    * that a plan is invalid, imports may be fulfilled by a parent
    * `Locator`, by BootstrapContext, or they may be materialized by
    * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
    *
    * @return this plan or a list of unresolved parameters
    */
  final def unresolvedImports: Either[Seq[ImportDependency], OrderedPlan] = {
    val nonMagicImports = getImports.filter {
      case i if i.target == DIKey.get[LocatorRef] =>
        false
      case _ =>
        true
    }
    if (nonMagicImports.isEmpty) Right(this) else Left(nonMagicImports)
  }

  /** Same as [[unresolvedImports]], but returns a pretty-printed exception if there are unresolved imports */
  final def assertImportsResolved: Either[InvalidPlanException, OrderedPlan] = {
    import izumi.fundamentals.platform.strings.IzString._
    unresolvedImports.left.map {
      unresolved =>
        new InvalidPlanException(unresolved.map(op => MissingInstanceException.format(op.target, op.references)).niceList(shift = ""))
    }
  }

  /**
    * Same as [[unresolvedImports]], but throws an [[InvalidPlanException]] if there are unresolved imports
    *
    * @throws InvalidPlanException
    */
  final def assertImportsResolvedOrThrow(): Unit = {
    assertImportsResolved.fold(throw _, _ => ())
  }

  /**
    * Be careful, don't use this method blindly, it can disrupt graph connectivity when used improperly.
    *
    * Proper usage assume that `keys` contains complete subgraph reachable from graph roots.
    */
  final def replaceWithImports(keys: Set[DIKey]): OrderedPlan = {
    val newSteps = steps.flatMap {
      case s if keys.contains(s.target) =>
        val dependees = topology.dependees.direct(s.target)
        if (dependees.diff(keys).nonEmpty || declaredRoots.contains(s.target)) {
          val dependees = topology.dependees.transitive(s.target).diff(keys)
          Seq(ImportDependency(s.target, dependees, s.origin.toSynthetic))
        } else {
          Seq.empty
        }
      case s =>
        Seq(s)
    }

    OrderedPlan(
      newSteps,
      declaredRoots,
      topology.removeKeys(keys),
    )
  }

  override final def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlanOps.resolveImports(AbstractPlanOps.importToInstances(f), steps))
  }

  override final def resolveImport[T: Tag](instance: T): OrderedPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override final def resolveImport[T: Tag](id: String)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override final def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }

  override final def toSemi: SemiPlan = {
    val safeSteps: Seq[SemiplanOp] = steps.flatMap{
      case s: SemiplanOp =>
        Seq(s)
      case s: ProxyOp =>
        s match {
          case m: MakeProxy =>
            Seq(m.op)
          case _: InitProxy =>
            Seq.empty
        }
    }
    SemiPlan(safeSteps.toVector, Roots(declaredRoots))
  }
}
