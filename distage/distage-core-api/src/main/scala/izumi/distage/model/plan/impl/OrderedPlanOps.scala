package izumi.distage.model.plan.impl

import izumi.distage.model.Locator
import izumi.distage.model.definition.Identifier
import izumi.distage.model.exceptions.{InvalidPlanException, MissingInstanceException}
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp, ProxyOp, SemiplanOp}
import izumi.distage.model.plan.{OrderedPlan, Roots, SemiPlan}
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.{DIKey, _}
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.reflect.{Tag, TagK}

private[plan] trait OrderedPlanOps extends Any { this: OrderedPlan =>

  /**
    * Same as [[unresolvedImports]], but throws an [[izumi.distage.model.exceptions.InvalidPlanException]] if there are unresolved imports
    *
    * @throws izumi.distage.model.exceptions.InvalidPlanException if there are unresolved imports
    */
  final def assertImportsResolvedOrThrow[F[_]: TagK](): Unit = {
    assertImportsResolved.fold(())(throw _)
  }

  /** Same as [[unresolvedImports]], but returns a pretty-printed exception if there are unresolved imports */
  final def assertImportsResolved[F[_]: TagK]: Option[InvalidPlanException] = {
    import izumi.fundamentals.platform.strings.IzString._
    unresolvedImports.map {
      unresolved =>
        new InvalidPlanException(unresolved.map(op => MissingInstanceException.format(op.target, op.references)).toList.niceList(shift = ""))
    }
  }

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
  final def unresolvedImports: Option[NonEmptyList[ImportDependency]] = {
    val locatorRefKey = DIKey[LocatorRef]
    val nonMagicImports = steps
      .iterator.collect {
        case i: ImportDependency if i.target != locatorRefKey => i
      }.toList
    NonEmptyList.from(nonMagicImports)
  }

  final def incompatibleEffectType[F[_]: TagK]: Option[NonEmptyList[MonadicOp]] = {
    val effectType = SafeType.getK[F]
    val badSteps = steps
      .iterator.collect {
        case op: MonadicOp if !(op.effectHKTypeCtor <:< effectType || op.effectHKTypeCtor <:< SafeType.identityEffectType) => op
      }.toList
    NonEmptyList.from(badSteps)
  }

  /**
    * Be careful, don't use this method blindly, it can disrupt graph connectivity when used improperly.
    *
    * Proper usage assume that `keys` contains complete subgraph reachable from graph roots.
    *
    * Note: this processes a complete plan, if you have bindings you can achieve a similar transformation before planning
    *       by deleting the `keys` from bindings: `module -- keys`
    */
  final def replaceWithImports(keys: Set[DIKey]): OrderedPlan = {
    val newSteps = steps.flatMap {
      case s if keys.contains(s.target) =>
        val dependees = topology.dependees.direct(s.target)
        if (dependees.diff(keys).nonEmpty || declaredRoots.contains(s.target)) {
          val dependees = topology.dependees.transitive(s.target).diff(keys)
          Seq(ImportDependency(s.target, dependees, s.origin.value.toSynthetic))
        } else {
          Seq.empty
        }
      case s =>
        Seq(s)
    }

    OrderedPlan(
      steps = newSteps,
      declaredRoots = declaredRoots,
      topology = topology.removeKeys(keys),
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

  override final def resolveImport[T: Tag](id: Identifier)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override final def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }

  override final def toSemi: SemiPlan = {
    val safeSteps: Seq[SemiplanOp] = steps.flatMap {
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
