package izumi.distage.sbt

import izumi.distage.testkit.protocol.*

import sbt.testing.{NestedTestSelector, OptionalThrowable, Status, SuiteSelector, TaskDef}

object TargetHostInspectionTest {
  def main(arguments: Array[String]): Unit = {
    require(arguments.isEmpty)
    for (operation <- Vector(RequestOperation.Resolve, RequestOperation.Plan); scenario <- Vector("success", "truncated", "reordered", "owner-changed")) {
      check(operation, scenario)
    }
    println("TARGET_HOST_INSPECTION_CHECK_OK scenarios=8")
  }

  private def check(operation: RequestOperation, scenario: String): Unit = {
    val run = RunId("inspection-frame")
    val suite = SuiteId("fixture.LargeSuite")
    val identity = CatalogueIdentity(BuildId("fixture"), BuildTargetId("fixture/native"), CatalogueId("fixture"))
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val tests = (1 to 300).toVector.map { index =>
      val path = Vector("large inspection " + index, "x" * 200)
      TestDescriptor(TestId(identity.target, suite, path, None), path.mkString(" "), SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    }
    val catalogue = Catalogue(identity, Vector(SuiteDescriptor(suite, "WordSpec")), tests)
    val resolved = ResolvedSelection(request, tests)
    val response: ProtocolMessage = operation match {
      case RequestOperation.Resolve => ProtocolMessage.Resolved(run, resolved)
      case RequestOperation.Plan => ProtocolMessage.Planned(run, PlannedSelection(resolved, PlanInspection.individualTests(tests.map(_.id))))
      case RequestOperation.Execute => throw new IllegalArgumentException("Unexpected fixture operation")
    }
    require(ProtocolCodec.encode(response).length > SdkProtocolFrames.MaxModifiedUtfBytes, "Inspection fixture must exceed the SDK writeUTF limit")
    val fingerprint = SdkFixtures.subclass(false, "izumi.distage.testkit.runner.TestSuite", true)
    val definition = new TaskDef(suite.value, fingerprint, false, Array(new SuiteSelector))
    val platform = SdkFixtures.framework("fixture", fingerprint, (arguments, remote, _) =>
      SdkFixtures.runner(arguments, remote, definitions => {
        require(definitions.toVector == Vector(definition))
        Array(SdkFixtures.task(definition, Array.empty, handler => {
          def send(message: ProtocolMessage, terminal: Boolean): Unit = {
            val encoded = SdkProtocolFrames.encode(ProtocolCodec.encode(message))
            val parts = if (terminal && scenario == "truncated") encoded.dropRight(1)
              else if (terminal && scenario == "reordered") encoded.reverse
              else encoded
            parts.zipWithIndex.foreach { case (part, index) =>
              val owner = if (terminal && scenario == "owner-changed" && index == 1) "another.Suite" else suite.value
              handler.handle(SdkFixtures.event(owner, definition.fingerprint(), new NestedTestSelector(part.selectorId, part.payload), Status.Success, new OptionalThrowable, 0L))
            }
          }
          send(ProtocolMessage.Discovered(run, catalogue), terminal = false)
          send(response, terminal = true)
          Array.empty
        }))
      }, () => "")
    )
    var logged = Vector.empty[String]
    val failure = try {
      TargetHostInspection.run(platform, Vector(definition), request, operation, line => logged :+= line)
      None
    } catch { case cause: IllegalArgumentException => Some(cause) }
    if (scenario == "success") {
      require(failure.isEmpty && logged == Vector("DISTAGE_INSPECTION " + ProtocolCodec.encode(response)), "Large inspection response changed during SDK projection")
    } else {
      val expected = scenario match {
        case "truncated" => "incomplete"
        case "reordered" => "first part"
        case "owner-changed" => "owner or length"
        case _ => throw new IllegalArgumentException("Unknown fixture scenario")
      }
      require(failure.exists(_.getMessage.contains(expected)) && logged.isEmpty, s"Inspection must reject $scenario before publishing a response")
    }
  }
}
