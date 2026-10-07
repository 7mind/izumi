package izumi.distage.planning

import distage.{GraphDumpBootstrapModule, Injector, ModuleDef, PlannerInput}
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

final class NativeGraphDumpResource

final class NativeGraphDumpTest extends AnyWordSpec {
  "Native graph dump observer" should {
    "write the planned dependency graph to the filesystem" in {
      val injector = Injector(bootstrapOverrides = Seq(GraphDumpBootstrapModule))
      val module = new ModuleDef {
        make[NativeGraphDumpResource]
      }
      val plan = injector.planUnsafe(PlannerInput.everything(module))
      assert(plan.stepsUnordered.nonEmpty)
      val graph = new String(Files.readAllBytes(Paths.get("target", "plan-last-aftergc.gv")), StandardCharsets.UTF_8)
      assert(graph.contains("digraph"))
      assert(graph.contains("NativeGraphDumpResource"))
    }
  }
}
