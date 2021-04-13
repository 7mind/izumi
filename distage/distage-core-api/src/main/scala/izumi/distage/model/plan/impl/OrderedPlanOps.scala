package izumi.distage.model.plan.impl

import izumi.distage.model.Locator
import izumi.distage.model.definition.Identifier
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, InvalidPlanException, MissingInstanceException}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp}
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.{DIKey, _}
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.reflect.{Tag, TagK}

private[plan] trait OrderedPlanOps extends Any { this: OrderedPlan =>

  /**
    * Check for any unresolved dependencies,
    * or for any `make[_].fromEffect` or `make[_].fromResource` bindings that are incompatible with the passed `F`,
    * or for any other issue that would cause [[izumi.distage.model.Injector#produce Injector.produce]] to fail
    *
    * If this returns `F.unit` then the wiring is generally correct,
    * modulo runtime exceptions in user code,
    * and `Injector.produce` should succeed.
    *
    * However, presence of imports does not *always* mean
    * that a plan is invalid, imports may be fulfilled by a parent
    * `Locator`, by BootstrapContext, or they may be materialized by
    * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
    *
    * An effect is compatible if it's a subtype of `F` or is a type equivalent to [[izumi.fundamentals.platform.functional.Identity]] (e.g. `cats.Id`)
    *
    * Will `F.fail` the effect with [[izumi.distage.model.exceptions.InvalidPlanException]] if there are issues.
    *
    * @tparam F effect type to check against
    */
  final def assertValid[F[_]: QuasiIO: TagK](ignoredImports: DIKey => Boolean = Set.empty): F[Unit] = {
    isValid(ignoredImports).fold(QuasiIO[F].unit)(QuasiIO[F].fail(_))
  }

  /**
    * Same as [[assertValid]], but throws an [[izumi.distage.model.exceptions.InvalidPlanException]] if there are unresolved imports
    *
    * @throws izumi.distage.model.exceptions.InvalidPlanException if there are issues
    */
  final def assertValidOrThrow[F[_]: TagK](ignoredImports: DIKey => Boolean = Set.empty): Unit = {
    isValid(ignoredImports).fold(())(throw _)
  }

  /** Same as [[unresolvedImports]], but returns a pretty-printed exception if there are unresolved imports */
  final def isValid[F[_]: TagK](ignoredImports: DIKey => Boolean = Set.empty): Option[InvalidPlanException] = {
    import izumi.fundamentals.platform.strings.IzString._
    val unresolved = unresolvedImports(ignoredImports).fromNonEmptyList.map(op => MissingInstanceException.format(op.target, op.references))
    val effects = incompatibleEffectType[F].fromNonEmptyList.map(op => IncompatibleEffectTypesException.format(op, SafeType.getK[F], op.effectHKTypeCtor))
    for {
      allErrors <- NonEmptyList.from(unresolved ++ effects)
    } yield new InvalidPlanException(allErrors.toList.niceList(shift = ""), Some(this))
  }

  /**
    * Check for any unresolved dependencies.
    *
    * If this returns `None` then the wiring is generally correct,
    * modulo runtime exceptions in user code,
    * and `Injector.produce` should succeed.
    *
    * However, presence of imports does not *always* mean
    * that a plan is invalid, imports may be fulfilled by a parent
    * `Locator`, by BootstrapContext, or they may be materialized by
    * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
    *
    * @return a non-empty list of unresolved imports if present
    */
  final def unresolvedImports(ignoredImports: DIKey => Boolean = Set.empty): Option[NonEmptyList[ImportDependency]] = {
    val locatorRefKey = DIKey[LocatorRef]
    val nonMagicImports = steps.iterator.collect {
      case i: ImportDependency if i.target != locatorRefKey && !ignoredImports(i.target) => i
    }.toList
    NonEmptyList.from(nonMagicImports)
  }
  final def unresolvedImports: Option[NonEmptyList[ImportDependency]] = unresolvedImports()

  /**
    * Check for any `make[_].fromEffect` or `make[_].fromResource` bindings that are incompatible with the passed `F`.
    *
    * An effect is compatible if it's a subtype of `F` or is a type equivalent to [[izumi.fundamentals.platform.functional.Identity]] (e.g. `cats.Id`)
    *
    * @tparam F effect type to check against
    * @return a non-empty list of operations incompatible with `F` if present
    */
  final def incompatibleEffectType[F[_]: TagK]: Option[NonEmptyList[MonadicOp]] = {
    val effectType = SafeType.getK[F]
    val badSteps = steps.iterator.collect {
      case op: MonadicOp if op.effectHKTypeCtor != SafeType.identityEffectType && !(op.effectHKTypeCtor <:< effectType) => op
    }.toList
    NonEmptyList.from(badSteps)
  }

  /**
    * Be careful, don't use this method blindly, it can disrupt graph connectivity when used improperly.
    *
    * Proper usage assume that `keys` contains complete subgraph reachable from graph roots.
    *
    * @note this processes a complete plan, if you have bindings you can achieve a similar transformation before planning
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

  @deprecated("Renamed to `assertValidOrThrow` and somewhat obsoleted by `Injector().assert` & new compile-time checks in `izumi.distage.framework.PlanCheck`!", "1.0")
  final def assertImportsResolvedOrThrow[F[_]: TagK](): Unit = assertValidOrThrow[F]()

  /** Same as [[unresolvedImports]], but returns a pretty-printed exception if there are unresolved imports */
  @deprecated("Renamed to `isValid` and somewhat obsoleted by `Injector().verify` & new compile-time checks in `izumi.distage.framework.PlanCheck`!", "1.0")
  final def assertImportsResolved[F[_]: TagK]: Option[InvalidPlanException] = isValid[F]()

}
