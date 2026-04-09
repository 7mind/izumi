package izumi.distage.testkit.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.{ListImagesCmd, PullImageCmd}
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.core.DefaultDockerClientConfig
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker
import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import org.scalatest.wordspec.AnyWordSpec

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.Collections
import scala.concurrent.duration._

final class ContainerResourcePlatformPullTest extends AnyWordSpec {
  "ContainerResource.doPull" should {
    "propagate configured platform to docker pull" in {
      val recorder = PullRecorder()

      val config = Docker.ContainerConfig[Unit](
        image = "test/image:latest",
        ports = Seq.empty,
        platform = Some("linux/amd64"),
        pullAttempts = 0,
        pullTimeout = 1.second,
      )

      val resource = testResource(config, dockerClient(recorder))
      assertThrows[IntegrationCheckException](resource.pull(config.image))
      assert(recorder.imageName.contains(config.image))
      assert(recorder.platform.contains("linux/amd64"))
    }

    "skip pull platform when none is configured" in {
      val recorder = PullRecorder()

      val config = Docker.ContainerConfig[Unit](
        image = "test/image:latest",
        ports = Seq.empty,
        pullAttempts = 0,
        pullTimeout = 1.second,
      )

      val resource = testResource(config, dockerClient(recorder))
      assertThrows[IntegrationCheckException](resource.pull(config.image))
      assert(recorder.imageName.contains(config.image))
      assert(recorder.platform.isEmpty)
    }
  }

  private def testResource(config: Docker.ContainerConfig[Unit], rawClient: DockerClient): TestContainerResource = {
    new TestContainerResource(
      config = config,
      client = new DockerClientWrapper[Identity](
        rawClient = rawClient,
        rawClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
        clientConfig = Docker.ClientConfig(),
        labelsBase = Map.empty,
        labelsJvm = Map.empty,
        labelsUnique = Map.empty,
        logger = IzLogger(),
      ),
      logger = IzLogger(),
    )
  }

  private final class TestContainerResource(
    config: Docker.ContainerConfig[Unit],
    client: DockerClientWrapper[Identity],
    logger: IzLogger,
  ) extends ContainerResource[Identity, Unit](config, client, logger, Set.empty) {
    def pull(imageName: String): Unit = doPull(imageName, registry = None, registryAuth = None)
  }

  private final case class PullRecorder(
    var imageName: Option[String] = None,
    var platform: Option[String] = None,
  )

  private def dockerClient(recorder: PullRecorder): DockerClient = {
    Proxy
      .newProxyInstance(
        classOf[DockerClient].getClassLoader,
        Array(classOf[DockerClient]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            handleObjectMethod(proxy, "DockerClient", method, args).getOrElse {
              method.getName match {
                case "listImagesCmd" =>
                  listImagesCmd()
                case "pullImageCmd" =>
                  recorder.imageName = Some(args(0).asInstanceOf[String])
                  pullImageCmd(recorder)
                case other =>
                  throw new UnsupportedOperationException(s"Unexpected DockerClient method: $other")
              }
            }
          }
        },
      )
      .asInstanceOf[DockerClient]
  }

  private def listImagesCmd(): ListImagesCmd = {
    Proxy
      .newProxyInstance(
        classOf[ListImagesCmd].getClassLoader,
        Array(classOf[ListImagesCmd]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            handleObjectMethod(proxy, "ListImagesCmd", method, args).getOrElse {
              method.getName match {
                case "exec" =>
                  Collections.emptyList[Image]()
                case other =>
                  throw new UnsupportedOperationException(s"Unexpected ListImagesCmd method: $other")
              }
            }
          }
        },
      )
      .asInstanceOf[ListImagesCmd]
  }

  private def pullImageCmd(recorder: PullRecorder): PullImageCmd = {
    Proxy
      .newProxyInstance(
        classOf[PullImageCmd].getClassLoader,
        Array(classOf[PullImageCmd]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
            handleObjectMethod(proxy, "PullImageCmd", method, args).getOrElse {
              method.getName match {
                case "withPlatform" =>
                  recorder.platform = Some(args(0).asInstanceOf[String])
                  proxy.asInstanceOf[AnyRef]
                case "start" =>
                  throw new RuntimeException("pull failed")
                case other =>
                  throw new UnsupportedOperationException(s"Unexpected PullImageCmd method: $other")
              }
            }
          }
        },
      )
      .asInstanceOf[PullImageCmd]
  }

  private def handleObjectMethod(proxy: Any, ifaceName: String, method: Method, args: Array[AnyRef]): Option[AnyRef] = {
    method.getName match {
      case "toString" => Some(s"${ifaceName}Proxy")
      case "hashCode" => Some(Int.box(System.identityHashCode(proxy)))
      case "equals" =>
        Some(Boolean.box(args != null && args.length == 1 && proxy.asInstanceOf[AnyRef].eq(args(0))))
      case _ =>
        None
    }
  }
}
