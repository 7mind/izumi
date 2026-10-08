package izumi.distage.testkit.protocol

final case class DependencyKeyId(value: Int) extends AnyVal
final case class DependencyKey(id: DependencyKeyId, displayName: String)
final case class PlanScopeId(path: Vector[Int])

sealed trait PlanOperation
object PlanOperation {
  case object Import extends PlanOperation
  case object LocatorReference extends PlanOperation
  case object CreateSet extends PlanOperation
  case object CallProvider extends PlanOperation
  case object UseInstance extends PlanOperation
  case object ReferenceKey extends PlanOperation
  case object CreateSubcontext extends PlanOperation
  case object ExecuteEffect extends PlanOperation
  case object AllocateResource extends PlanOperation
  case object MakeProxy extends PlanOperation
  case object InitProxy extends PlanOperation
}

sealed trait PlanScopeKind
object PlanScopeKind {
  case object Runtime extends PlanScopeKind
  case object Memoization extends PlanScopeKind
  case object Test extends PlanScopeKind
}

final case class PlanStep(key: DependencyKeyId, operation: PlanOperation, dependencies: Vector[DependencyKeyId])
final case class PlanScope(id: PlanScopeId, kind: PlanScopeKind, tests: Vector[TestId], steps: Vector[PlanStep])
final case class PlanFailure(tests: Vector[TestId], failure: Failure)
final case class PlanInspection(keys: Vector[DependencyKey], scopes: Vector[PlanScope], failures: Vector[PlanFailure]) {
  def validate(selected: Vector[TestId]): Either[String, Unit] = {
    val byId = scopes.map(scope => scope.id -> scope).toMap
    val declaredKeys = keys.map(_.id).toSet
    val childrenByParent = scopes.filter(_.id.path.size > 1).groupBy(scope => PlanScopeId(scope.id.path.dropRight(1)))
    val leaves = scopes.filter(_.kind == PlanScopeKind.Test).flatMap(_.tests)
    val failed = failures.flatMap(_.tests)
    if (keys.map(_.id).distinct.size != keys.size) Left("Duplicate plan key identities")
    else if (keys.exists(key => key.id.value < 0 || key.displayName.isEmpty)) Left("Invalid plan key description")
    else if (scopes.exists(_.steps.exists(step => !(step.key +: step.dependencies).forall(declaredKeys.contains)))) Left("Unknown plan dependency key identity")
    else if (byId.size != scopes.size) Left("Duplicate plan scope identities")
    else if (scopes.exists(scope => scope.id.path.isEmpty || scope.id.path.exists(_ < 0))) Left("Invalid plan scope path")
    else if (scopes.exists(scope => scope.tests.distinct.size != scope.tests.size || scope.tests.exists(test => !selected.contains(test)))) Left("Invalid plan scope test identities")
    else if (scopes.exists(scope => scope.steps.map(_.key).distinct.size != scope.steps.size || scope.steps.exists(step => step.dependencies.distinct.size != step.dependencies.size))) Left("Duplicate plan dependency keys")
    else if (failures.exists(value => value.tests.isEmpty || value.failure.phase != FailurePhase.Planning)) Left("Invalid per-test planning failure")
    else if ((leaves ++ failed).distinct.size != leaves.size + failed.size || (leaves ++ failed).toSet != selected.toSet) Left("Plan leaves and failures must cover the selected tests exactly once")
    else {
      scopes.foldLeft[Either[String, Unit]](Right(())) { (previous, scope) =>
        previous.flatMap { _ =>
          val children = childrenByParent.getOrElse(scope.id, Vector.empty)
          val parent = byId.get(PlanScopeId(scope.id.path.dropRight(1)))
          val available = scopes.filter(ancestor => scope.id.path.startsWith(ancestor.id.path)).flatMap(_.steps.map(_.key)).toSet
          if (scope.id.path.size > 1 && parent.isEmpty) Left("Plan scope parent is missing")
          else if (scope.kind == PlanScopeKind.Runtime && scope.id.path.size != 1) Left("Runtime plan scopes must be roots")
          else if (scope.kind == PlanScopeKind.Memoization && parent.isEmpty) Left("Memoization plan scopes require a parent")
          else if (scope.kind == PlanScopeKind.Test && (scope.tests.size != 1 || children.nonEmpty)) Left("Test plan scopes must be individual leaves")
          else if (scope.kind != PlanScopeKind.Test && (children.flatMap(_.tests).distinct.size != children.flatMap(_.tests).size || children.flatMap(_.tests).toSet != scope.tests.toSet)) Left("Plan scope children must partition their parent's tests")
          else if (scope.steps.exists(_.dependencies.exists(key => !available.contains(key)))) Left("Plan dependency has no operation in its scope or ancestor")
          else Right(())
        }
      }
    }
  }
}

object PlanInspection {
  def individualTests(tests: Vector[TestId]): PlanInspection = PlanInspection(
    Vector.empty,
    tests.zipWithIndex.map { case (test, index) => PlanScope(PlanScopeId(Vector(index)), PlanScopeKind.Test, Vector(test), Vector.empty) },
    Vector.empty,
  )
}

final case class ResolvedSelection(request: RunRequest, tests: Vector[TestDescriptor])
final case class PlannedSelection(selection: ResolvedSelection, inspection: PlanInspection)
