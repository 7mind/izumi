package izumi.distage.planning

import izumi.distage.model.definition.conflicts.MutSel
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.errors.DIError.{ConflictResolutionFailed, LoopResolutionError}
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.exceptions.InjectorFailed
import izumi.distage.model.exceptions.planning.{ConflictResolutionException, DIBugException}
import izumi.distage.model.plan.*
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, SemiplanOp}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.planning.*
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Planner, PlannerInput}
import izumi.distage.planning.solver.{PlanSolver, SemigraphSolver}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{DG, GraphMeta}

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class PlannerDefaultImpl(
  forwardingRefResolver: ForwardingRefResolver,
  sanityChecker: SanityChecker,
  planningObserver: PlanningObserver,
  hook: PlanningHook,
  resolver: PlanSolver,
) extends Planner {

  import scala.collection.compat.*

  override def planSafe(input: PlannerInput): Either[List[DIError], Plan] = {
    planNoRewriteSafe(input.copy(bindings = rewrite(input.bindings)))

  }

  override def planNoRewriteSafe(input: PlannerInput): Either[List[DIError], Plan] = {
    for {
      resolved <- resolver.resolveConflicts(input).left.map(e => e.map(ConflictResolutionFailed.apply))
      plan = preparePlan(resolved)
      withImports = addImports(plan, input.roots)
      withoutLoops <- forwardingRefResolver.resolveMatrix(withImports)
      _ <- sanityChecker.verifyPlan(withoutLoops, input.roots)
      _ <- Right(planningObserver.onPlanningFinished(input, withoutLoops))
    } yield {
      Plan(withoutLoops, input)
    }
  }

  override def plan(input: PlannerInput): Plan = {
    planSafe(input) match {
      case Left(errors) =>
        throwOnError(input.activation, errors)

      case Right(resolved) =>
        resolved
    }
  }

  override def planNoRewrite(input: PlannerInput): Plan = {
    planNoRewriteSafe(input) match {
      case Left(errors) =>
        throwOnError(input.activation, errors)

      case Right(resolved) =>
        resolved
    }
  }

  private def preparePlan(resolved: DG[MutSel[DIKey], SemigraphSolver.RemappedValue[InstantiationOp, DIKey]]) = {
    val mappedGraph = resolved.predecessors.links.toSeq.map {
      case (target, deps) =>
        val mappedTarget = updateKey(target)
        val mappedDeps = deps.map(updateKey)

        val op = resolved.meta.nodes.get(target).map {
          op =>
            val remaps = op.remapped.map {
              case (original, remapped) =>
                (original, updateKey(remapped))
            }
            val mapper = (key: DIKey) => remaps.getOrElse(key, key)
            op.meta.replaceKeys(key => Map(op.meta.target -> mappedTarget).getOrElse(key, key), mapper)
        }

        ((mappedTarget, mappedDeps), op.map(o => (mappedTarget, o)))
    }

    val mappedOps = mappedGraph.view.flatMap(_._2).toMap

    val mappedMatrix = mappedGraph.view.map(_._1).filter { case (k, _) => mappedOps.contains(k) }.toMap
    val plan = DG.fromPred(IncidenceMatrix(mappedMatrix), GraphMeta(mappedOps))
    plan
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    hook.hookDefinition(module)
  }

  protected[this] def updateKey(mutSel: MutSel[DIKey]): DIKey = {
    mutSel.mut match {
      case Some(value) =>
        updateKey(mutSel.key, value)
      case None =>
        mutSel.key
    }
  }

  protected[this] def updateKey(key: DIKey, mindex: Int): DIKey = {
    key match {
      case DIKey.TypeKey(tpe, _) =>
        DIKey.TypeKey(tpe, Some(mindex))
      case k @ DIKey.IdKey(_, _, _) =>
        k.withMutatorIndex(Some(mindex))
      case s: DIKey.SetElementKey =>
        s.copy(set = updateKey(s.set, mindex))
      case r: DIKey.ResourceKey =>
        r.copy(key = updateKey(r.key, mindex))
      case e: DIKey.EffectKey =>
        e.copy(key = updateKey(e.key, mindex))
      case k =>
        throw DIBugException(s"Unexpected key mutator: $k, m=$mindex")
    }
  }

  @nowarn("msg=Unused import")
  protected[this] def addImports(plan: DG[DIKey, InstantiationOp], roots: Roots): DG[DIKey, SemiplanOp] = {

    val imports = plan.successors.links.view
      .filterKeys(k => !plan.meta.nodes.contains(k))
      .map {
        case (missing, refs) =>
          val maybeFirstOrigin = refs.headOption.flatMap(key => plan.meta.nodes.get(key)).map(_.origin.value.toSynthetic)
          val origin = EqualizedOperationOrigin(maybeFirstOrigin.getOrElse(OperationOrigin.Unknown))
          (missing, ImportDependency(missing, refs, origin))
      }
      .toMap

    val missingRoots = roots match {
      case Roots.Of(roots) =>
        roots.toSet
          .diff(plan.meta.nodes.keySet)
          .diff(imports.keySet)
          .map {
            root =>
              (root, ImportDependency(root, Set.empty, OperationOrigin.Unknown))
          }
          .toVector
      case Roots.Everything =>
        Vector.empty
    }

    val missingRootsImports = missingRoots.toMap

    val allImports = (imports.keySet ++ missingRootsImports.keySet).map {
      i =>
        (i, Set.empty[DIKey])
    }

    val fullMeta = GraphMeta(plan.meta.nodes ++ imports ++ missingRootsImports)

    DG.fromPred(IncidenceMatrix(plan.predecessors.links ++ allImports), fullMeta)
  }

  // TODO: we need to completely get rid of exceptions, this is just some transitional stuff
  protected[this] def throwOnError(activation: Activation, issues: List[DIError]): Nothing = {
    val conflicts = issues.collect { case c: ConflictResolutionFailed => c }
    if (conflicts.nonEmpty) {
      throwOnConflict(activation, conflicts)
    }
    import izumi.fundamentals.platform.strings.IzString.*

    val loops = issues.collect { case e: LoopResolutionError => DIError.formatError(e) }.niceList()
    if (loops.nonEmpty) {
      throw new InjectorFailed(
        s"""Injector failed unexpectedly. List of issues: $loops
       """.stripMargin,
        issues,
      )
    }

    val inconsistencies = issues.collect { case e: DIError.VerificationError => DIError.formatError(e) }.niceList()
    if (inconsistencies.nonEmpty) {
      throw new InjectorFailed(
        s"""Injector failed unexpectedly. List of issues: $loops
       """.stripMargin,
        issues,
      )
    }

    throw new InjectorFailed("BUG: Injector failed and is unable to provide any diagnostics", List.empty)
  }
  protected[this] def throwOnConflict(activation: Activation, issues: List[ConflictResolutionFailed]): Nothing = {
    val rawIssues = issues.map(_.error)
    val issueRepr = rawIssues.map(DIError.formatConflict(activation)).mkString("\n", "\n", "")

    throw new ConflictResolutionException(
      s"""Found multiple instances for a key. There must be exactly one binding for each DIKey. List of issues:$issueRepr
         |
         |You can use named instances: `make[X].named("id")` syntax and `distage.Id` annotation to disambiguate between multiple instances of the same type.
       """.stripMargin,
      rawIssues,
    )
  }

}
