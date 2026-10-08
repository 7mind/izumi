package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.runner.spec.{AnyWordSpec, AsyncWordSpec}

import sbt.testing.{Event, EventHandler, Status, SuiteSelector, TaskDef}

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*

object WorkerClassLoaderFixtures {
  private[bootstrap] final val ResourceName = "distage-bootstrap-worker-loader.txt"

  def main(args: Array[String]): Unit = {
    val parent = getClass.getClassLoader
    require(parent.getResource(ResourceName) == null, "Resource must be absent from the parent test loader")
    val directory = Files.createTempDirectory("distage-worker-loader-")
    val resource = directory.resolve(ResourceName)
    val _ = Files.write(resource, "session-owned".getBytes(StandardCharsets.UTF_8))
    val loader = new URLClassLoader(Array(directory.toUri.toURL), parent)
    try {
      require(loader.getResource(ResourceName) != null, "Child test loader must resolve its resource")
      val framework = new Framework
      val flags = Array("--build-id", "loader-build", "--target-id", "loader-target", "--catalogue-id", "loader-catalogue")
      val runner = framework.runner(flags, Array.empty, loader)
      val events = new ConcurrentLinkedQueue[Event]()
      val handler = new EventHandler { override def handle(event: Event): Unit = { val _ = events.add(event) } }
      try {
        val definitions = Array(classOf[WorkerClassLoaderSyncSuite], classOf[WorkerClassLoaderAsyncSuite]).map {
          suite => new TaskDef(suite.getName, framework.fingerprints().head, false, Array(new SuiteSelector))
        }
        runner.tasks(definitions).foreach { task => require(task.execute(handler, Array.empty).isEmpty, "Loader suites must complete normally") }
        val results = events.asScala.toVector
        require(results.size == 2 && results.forall(_.status() == Status.Success), "Worker loader resource checks failed: " + results.map(event => (event.status(), event.throwable())))
        println("WORKER_CLASSLOADER_FIXTURES_OK cases=2 child-resource=visible sync+future=true")
      } finally { val _ = runner.done() }
    } finally {
      loader.close()
      val _ = Files.deleteIfExists(resource)
      val _ = Files.deleteIfExists(directory)
    }
  }

  private[bootstrap] def verifyResource(): Unit = {
    val resource = Thread.currentThread().getContextClassLoader.getResource(ResourceName)
    require(resource != null, "Runner worker cannot resolve the resource supplied through the test classloader")
    val input = resource.openStream()
    try require(new String(input.readAllBytes(), StandardCharsets.UTF_8) == "session-owned", "Runner worker resolved another loader's resource")
    finally input.close()
  }
}

final class WorkerClassLoaderSyncSuite extends AnyWordSpec {
  "Synchronous bodies" should {
    "use the supplied test classloader" in WorkerClassLoaderFixtures.verifyResource()
  }
}

final class WorkerClassLoaderAsyncSuite extends AsyncWordSpec {
  "Future bodies" should {
    "use the supplied test classloader" in Future { WorkerClassLoaderFixtures.verifyResource() }
  }
}
