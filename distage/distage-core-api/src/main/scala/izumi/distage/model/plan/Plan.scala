package izumi.distage.model.plan

import izumi.distage.model.definition.{Identifier, ModuleBase}
import izumi.distage.model.exceptions.runtime.ToposortFailed
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp}
import izumi.distage.model.plan.Wiring.SingletonWiring.Instance
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.{DIPlanCompactFormatter, DepTreeRenderer}
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.model.{Locator, PlannerInput}
import izumi.functional.Renderable
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}
import izumi.fundamentals.graphs.{DG, GraphMeta}
import izumi.reflect.{Tag, TagK}

import scala.annotation.nowarn

final case class Plan(
  plan: DG[DIKey, ExecutableOp],
  input: PlannerInput,
) {
  // TODO: equals/hashcode should not be used under normal circumstances. Currently we need them for "memoization levels" to work but we have to get rid of that
  override def hashCode(): Int = {
    this.plan.meta.hashCode() ^ this.plan.predecessors.hashCode()
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: Plan =>
      this.plan.meta == that.plan.meta &&
      this.plan.predecessors == that.plan.predecessors
    case _ => false
  }

  override def toString: String = {
    this.render()
  }
}

object Plan {
  def empty: Plan = Plan(
    DG(IncidenceMatrix.empty, IncidenceMatrix.empty, GraphMeta.empty),
    PlannerInput.everything(ModuleBase.empty),
  )

  @inline implicit final def defaultFormatter: Renderable[Plan] = DIPlanCompactFormatter

  implicit final class DIPlanSyntax(private val plan: Plan) extends AnyVal {
    def keys: Set[DIKey] = plan.plan.meta.nodes.keySet

    def stepsUnordered: Iterable[ExecutableOp] = plan.plan.meta.nodes.values

    /** Effective [[ModuleBase bindings]] of this plan */
    def definition: ModuleBase = {
      val userBindings = plan.stepsUnordered.flatMap {
        _.origin.value match {
          case OperationOrigin.UserBinding(binding) =>
            Seq(binding)
          case _ =>
            Seq.empty
        }
      }.toSet
      ModuleBase.make(userBindings)
    }

    /** Original [[ModuleBase bindings]] of this plan */
    def definitionOriginal: ModuleBase = plan.input.bindings

    /**
      * This is only used by plan formatter
      */
    def toposort: Seq[DIKey] = {
      Toposort.cycleBreaking(plan.plan.predecessors, ToposortLoopBreaker.breakOn[DIKey](_.headOption)) match {
        case Left(value) =>
          throw new ToposortFailed(value)
        case Right(value) =>
          value
      }
    }

    /**
      * Be careful, don't use this method blindly, it can disrupt graph connectivity when used improperly.
      *
      * Proper usage assume that `keys` contains complete subgraph reachable from graph roots.
      *
      * @note this processes a completed plan, you can achieve a similar transformation
      *       before planning by removing the `keys` from [[ModuleBase]]:
      *       `module -- keys`
      */
    def replaceWithImports(keys: Set[DIKey]): Plan = {
      val newImports = keys.flatMap {
        k =>
          val dependees = plan.plan.successors.links(k)
          val dependeesWithoutKeys = dependees.diff(keys)
          if (dependeesWithoutKeys.nonEmpty || plan.plan.noSuccessors.contains(k)) {
            Seq((k, ImportDependency(k, dependeesWithoutKeys, plan.plan.meta.nodes(k).origin.value.toSynthetic)))
          } else {
            Seq.empty
          }
      }

      val replaced = newImports.toMap
      val removed = keys -- replaced.keySet

      val s = IncidenceMatrix(plan.plan.predecessors.without(removed).links ++ replaced.keys.map(k => (k, Set.empty[DIKey])))
      val m = GraphMeta(plan.plan.meta.without(removed).nodes ++ replaced)
      Plan(DG(s.transposed, s, m), plan.input)
    }

    def render()(implicit ev: Renderable[Plan]): String = ev.render(plan)

    def renderDeps(key: DIKey): String = {
      val dg = new DependencyGraph(plan.plan.predecessors, DependencyGraph.DependencyKind.Depends)
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }

    def renderDependees(key: DIKey): String = {
      val dg = new DependencyGraph(plan.plan.successors, DependencyGraph.DependencyKind.Required)
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }

    def renderAllDeps(): String = {
      val effectiveRoots = plan.plan.noSuccessors
      effectiveRoots.map(renderDeps).mkString("\n")
    }
  }

  implicit final class DIPlanAssertionSyntax(private val plan: Plan) extends AnyVal {

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
      *
      * @see [[distage.Injector#assert]] for a check you can use in tests
      */
    def unresolvedImports(ignoredImports: DIKey => Boolean = Set.empty): Option[NonEmptyList[ImportDependency]] = {
      val locatorRefKey = DIKey[LocatorRef]
      val nonMagicImports = plan.stepsUnordered.iterator.collect {
        case i: ImportDependency if i.target != locatorRefKey && !ignoredImports(i.target) => i
      }.toList
      NonEmptyList.from(nonMagicImports)
    }

    /**
      * Check for any `make[_].fromEffect` or `make[_].fromResource` bindings that are incompatible with the passed `F`.
      *
      * An effect is compatible if it's a subtype of `F` or is a type equivalent to [[izumi.fundamentals.platform.functional.Identity]] (e.g. `cats.Id`)
      *
      * @tparam F effect type to check against
      * @return a non-empty list of operations incompatible with `F` if present
      */
    def incompatibleEffectType[F[_]: TagK]: Option[NonEmptyList[MonadicOp]] = {
      val effectType = SafeType.getK[F]
      val badSteps = plan.stepsUnordered.iterator.collect {
        case op: MonadicOp if op.effectHKTypeCtor != SafeType.identityEffectType && !(op.effectHKTypeCtor <:< effectType) => op
      }.toList
      NonEmptyList.from(badSteps)
    }

    /**
      * Get all imports (unresolved dependencies).
      *
      * Note, presence of imports does not *always* mean
      * that a plan is invalid, imports may be fulfilled by a parent
      * `Locator`, by BootstrapContext, or they may be materialized by
      * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
      *
      * @see [[distage.Injector#assert]] for a check you can use in tests
      */
    def allImports: Iterable[ImportDependency] = {
      plan.stepsUnordered.collect { case i: ImportDependency => i }
    }

  }

  implicit final class DIPlanResolveImportsSyntax(private val plan: Plan) extends AnyVal {
    def locateImports(locator: Locator): Plan = {
      resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
    }

    @nowarn("msg=Unused import")
    def resolveImports(f: PartialFunction[ImportDependency, Any]): Plan = {
      import scala.collection.compat.*

      val dg = plan.plan
      plan.copy(plan = dg.copy(meta = GraphMeta(dg.meta.nodes.view.mapValues {
        case i: ImportDependency =>
          f.andThen(instance => UseInstance(i.target, Instance(i.target.tpe, instance), i.origin)).applyOrElse(i, (_: ImportDependency) => i)
        case op =>
          op
      }.toMap)))
    }

    def resolveImport[T: Tag](instance: T): Plan = {
      resolveImports {
        case i if i.target == DIKey.get[T] =>
          instance
      }
    }

    def resolveImport[T: Tag](id: Identifier)(instance: T): Plan = {
      resolveImports {
        case i if i.target == DIKey.get[T].named(id) =>
          instance
      }
    }
  }

}
