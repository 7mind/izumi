package izumi.distage.testkit.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.ListContainersCmd
import distage.ModuleDef
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.{ContainerId, DockerPort, DockerReusePolicy}
import izumi.distage.testkit.docker.ContainerFilesTest.*
import izumi.distage.testkit.docker.ReuseCandidateTest.*
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec2}
import izumi.fundamentals.platform.language.Quirks.*
import zio.{IO, Task, ZIO}

import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method, Proxy}
import java.util.concurrent.atomic.AtomicBoolean

object ReuseCandidateTest {
  final val port: DockerPort = DockerPort.TCP(8080)

  final class Delegating[A <: AnyRef](target: A, intercept: PartialFunction[(String, AnyRef), AnyRef]) extends InvocationHandler {
    override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
      val result =
        try {
          method.invoke(target, Option(args).getOrElse(Array.empty[AnyRef])*)
        } catch {
          case failure: InvocationTargetException => throw failure.getCause
        }
      if (result eq target) proxy else intercept.applyOrElse((method.getName, result), (_: (String, AnyRef)) => result)
    }
  }

  def delegating[A <: AnyRef](iface: Class[A], target: A)(intercept: PartialFunction[(String, AnyRef), AnyRef]): A = {
    iface.cast(Proxy.newProxyInstance(iface.getClassLoader, Array[Class[?]](iface), new Delegating(target, intercept)))
  }

  def removingAfterFirstListing[T](resource: ContainerResource[Task, T], removed: ContainerId): ContainerResource[Task, T] = {
    val client = resource.client
    val raw = client.rawClient
    val firstListing = new AtomicBoolean(true)
    val intercepting = delegating(classOf[DockerClient], raw) {
      case ("listContainersCmd", cmd: ListContainersCmd) =>
        delegating(classOf[ListContainersCmd], cmd) {
          case ("exec", listed) =>
            if (firstListing.getAndSet(false)) {
              raw.removeContainerCmd(removed.name).withForce(true).exec().discard()
            }
            listed
        }
    }
    val interceptingClient = new DockerClientWrapper[Task](
      rawClient = intercepting,
      rawClientConfig = client.rawClientConfig,
      clientConfig = client.clientConfig,
      labelsBase = client.labelsBase,
      labelsJvm = client.labelsJvm,
      labelsUnique = client.labelsUnique,
      logger = resource.logger,
    )(resource.F)
    resource.copy(client = interceptingClient)
  }
}

final class ReuseCandidateTest extends Spec2[IO] with AssertZIO {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ContainerResource[Task, RunningContainer.Tag]].from(RunningContainer.make[Task])
    }
  )

  "Container reuse" should {

    "skip a candidate container that is removed after it was listed" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        ZIO.scoped {
          for {
            containers <- testContainers(resource, client)
            reusable = containers.configured(_.copy(ports = Seq(port), reuse = DockerReusePolicy.ReuseEnabled))
            removedId <- reusable.use(container => ZIO.succeed(container.id))
            acquiredId <- removingAfterFirstListing(reusable, removedId).use(container => ZIO.succeed(container.id))
            _ <- assertIO(acquiredId != removedId)
          } yield ()
        }
    }

    "not reuse a running container that was created without reuse" in {
      (resource: ContainerResource[Task, RunningContainer.Tag], client: DockerClientWrapper[Task]) =>
        ZIO.scoped {
          for {
            containers <- testContainers(resource, client)
            disposable = containers.configured(_.copy(ports = Seq(port), reuse = DockerReusePolicy.ReuseDisabled, autoRemove = false))
            reusable = containers.configured(_.copy(ports = Seq(port), reuse = DockerReusePolicy.ReuseEnabled))
            ids <- disposable.use(disposableContainer => reusable.use(reusedContainer => ZIO.succeed((disposableContainer.id, reusedContainer.id))))
            (disposableId, reusedId) = ids
            _ <- assertIO(disposableId != reusedId)
          } yield ()
        }
    }

  }

}
