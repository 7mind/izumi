package izumi.distage.testkit.protocol

import io.circe.Json
import io.circe.parser.parse

private[protocol] object PlanInspectionFixtures {
  def run(
    run: RunId,
    request: RunRequest,
    tests: Vector[TestDescriptor],
    failure: Failure,
    verify: (Boolean, String) => Unit,
    reject: (ProtocolMessage, String) => Unit,
  ): Unit = {
    val ids = tests.map(_.id)
    val resource = DependencyKeyId(0)
    val pair = DependencyKeyId(1)
    val keys = Vector(DependencyKey(resource, "fixture.Resource"), DependencyKey(pair, "fixture.Pair"))
    val runtime = PlanScope(PlanScopeId(Vector(0)), PlanScopeKind.Runtime, ids, Vector.empty)
    val shared = PlanScope(PlanScopeId(Vector(0, 0)), PlanScopeKind.Memoization, ids, Vector(PlanStep(resource, PlanOperation.AllocateResource, Vector.empty)))
    val leaves = ids.zipWithIndex.map { case (id, index) =>
      PlanScope(PlanScopeId(Vector(0, 0, index)), PlanScopeKind.Test, Vector(id), Vector(
        PlanStep(resource, PlanOperation.Import, Vector.empty),
        PlanStep(pair, PlanOperation.CallProvider, Vector(resource)),
      ))
    }
    val inspection = PlanInspection(keys, Vector(runtime, shared) ++ leaves, Vector.empty)
    val selection = ResolvedSelection(request, tests)
    def message(value: PlanInspection): ProtocolMessage.Planned = ProtocolMessage.Planned(run, PlannedSelection(selection, value))
    val valid = message(inspection)
    verify(ProtocolCodec.decode(ProtocolCodec.encode(valid)) == Right(valid), "Nested runtime/shared/test scopes and dependency edges must round-trip")
    val sameLabels = message(inspection.copy(keys = keys.map(_.copy(displayName = "same label"))))
    verify(ProtocolCodec.decode(ProtocolCodec.encode(sameLabels)) == Right(sameLabels), "Distinct key identities may have equal display labels")
    reject(message(inspection.copy(keys = keys :+ keys.head)), "Duplicate plan key identities")
    reject(message(inspection.copy(keys = keys.updated(0, keys.head.copy(displayName = "")))), "Invalid plan key description")
    reject(message(inspection.copy(keys = keys.tail)), "Unknown plan dependency key")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(steps = Vector(PlanStep(resource, PlanOperation.CallProvider, Vector(pair))))))), "no operation in its scope or ancestor")
    val json = parse(ProtocolCodec.encode(valid)).fold(error => throw new IllegalStateException(error.message), value => value)
    val missingKey = json.hcursor.downField("message").downField("plan").downField("inspection").downField("keys").withFocus(_ => Json.arr()).top.get.noSpaces
    verify(ProtocolCodec.decode(missingKey).left.exists(_.message.contains("Unknown plan dependency key")), "Decoder must reject undeclared key references")
    val outsideScope = json.hcursor.downField("message").downField("plan").downField("inspection").downField("scopes").downArray.right.downField("steps")
      .withFocus(_ => Json.arr(Json.obj("key" -> Json.fromInt(resource.value), "operation" -> Json.fromString("callProvider"), "dependencies" -> Json.arr(Json.fromInt(pair.value))))).top.get.noSpaces
    verify(ProtocolCodec.decode(outsideScope).left.exists(_.message.contains("no operation in its scope or ancestor")), "Decoder must reject a reference whose operation exists only in descendant scopes")
    val operations = Vector(
      PlanOperation.Import, PlanOperation.LocatorReference, PlanOperation.CreateSet,
      PlanOperation.CallProvider, PlanOperation.UseInstance, PlanOperation.ReferenceKey,
      PlanOperation.CreateSubcontext, PlanOperation.ExecuteEffect, PlanOperation.AllocateResource,
      PlanOperation.MakeProxy, PlanOperation.InitProxy,
    )
    operations.foreach { operation =>
      val changed = message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(steps = Vector(PlanStep(resource, operation, Vector.empty))))))
      verify(ProtocolCodec.decode(ProtocolCodec.encode(changed)) == Right(changed), "Every dependency operation must round-trip: " + operation)
    }
    val failed = message(inspection.copy(
      scopes = Vector(runtime.copy(tests = ids.take(1)), shared.copy(tests = ids.take(1)), leaves.head),
      failures = Vector(PlanFailure(ids.drop(1), failure)),
    ))
    verify(ProtocolCodec.decode(ProtocolCodec.encode(failed)) == Right(failed), "Per-test Planning failures must remain separate from provisionable leaves")
    reject(message(inspection.copy(scopes = inspection.scopes :+ leaves.head)), "Duplicate plan scope")
    reject(message(inspection.copy(scopes = inspection.scopes.tail)), "parent is missing")
    reject(message(inspection.copy(scopes = inspection.scopes.init)), "cover the selected tests")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(0, runtime.copy(tests = ids.take(1))))), "partition their parent's tests")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(kind = PlanScopeKind.Runtime)))), "must be roots")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(0, runtime.copy(kind = PlanScopeKind.Memoization)))), "require a parent")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(kind = PlanScopeKind.Test)))), "cover the selected tests")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(steps = shared.steps ++ shared.steps)))), "Duplicate plan dependency")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(steps = Vector(PlanStep(resource, PlanOperation.CallProvider, Vector(pair, pair))))))), "Duplicate plan dependency")
    reject(message(inspection.copy(scopes = inspection.scopes.updated(1, shared.copy(id = PlanScopeId(Vector(-1)))))), "Invalid plan scope path")
    reject(message(inspection.copy(failures = Vector(PlanFailure(Vector.empty, failure)))), "Invalid per-test planning")
    reject(message(inspection.copy(failures = Vector(PlanFailure(ids, failure.copy(phase = FailurePhase.Test))))), "Invalid per-test planning")
    reject(message(inspection.copy(failures = Vector(PlanFailure(ids, failure)))), "cover the selected tests")
    reject(ProtocolMessage.Resolved(run, selection.copy(tests = Vector.empty)), "distinct tests")
    reject(ProtocolMessage.Resolved(run, selection.copy(tests = tests ++ tests)), "distinct tests")
    reject(ProtocolMessage.Resolved(run, selection.copy(request = request.copy(identity = request.identity.copy(target = BuildTargetId("another-target"))))), "target differs")
    reject(ProtocolMessage.Resolved(run, selection.copy(request = request.copy(selection = Selection.Only(Vector.empty, ids.take(1))))), "unselected test")
    reject(ProtocolMessage.Resolved(run, selection.copy(request = request.copy(overrides = request.overrides.copy(memoization = MemoizationOverride.Disabled)))), "memoization differs")
    reject(ProtocolMessage.Resolved(run, selection.copy(request = request.copy(overrides = request.overrides.copy(axes = Vector(AxisChoice(AxisId("unknown"), AxisValue("unknown"))))))), "activation differs")
  }
}
