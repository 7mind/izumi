package izumi.distage.testkit.docker

import com.github.dockerjava.api.exception.NotFoundException
import distage.{DefaultModule2, ModuleDef, TagKK}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.impl.{ContainerResource, DockerClientWrapper}
import izumi.distage.docker.model.Docker.DockerReusePolicy
import izumi.distage.testkit.docker.DockerPullWithPlatformTest.{HelloWorldRiscV64Docker, imageName}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.Spec2
import izumi.functional.bio.{F, IO2}
import org.scalatest.Assertion

final class DockerPullWithPlatformTestZIO extends DockerPullWithPlatformTest[zio.IO]

object DockerPullWithPlatformTest {
  val imageName = "library/hello-world:latest"

  object HelloWorldRiscV64Docker extends ContainerDef {
    override def config: Config = {
      Config(
        image = imageName,
        ports = Seq.empty,
        autoRemove = false,
        reuse = DockerReusePolicy.ReuseDisabled,
        // succeed: we only care about the image pull, not the container's runtime behavior;
        // the riscv64 binary won't execute on a non-riscv64 host anyway
        healthCheck = ContainerHealthCheck.succeed,
        platform = Some("linux/riscv64"),
      )
    }
  }
}

abstract class DockerPullWithPlatformTest[F[+_, +_]: DefaultModule2: TagKK: IO2] extends Spec2[F] {

  override protected def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      make[ContainerResource[F[Throwable, _], HelloWorldRiscV64Docker.Tag]]
        .from(HelloWorldRiscV64Docker.make[F[Throwable, _]])
    }
  )

  "Platform-specific image pull" should {

    "pull hello-world for linux/riscv64 and verify image architecture" in {
      (client: DockerClientWrapper[F[Throwable, _]], containerResource: ContainerResource[F[Throwable, _], HelloWorldRiscV64Docker.Tag]) =>
        def removeImage(): F[Nothing, Unit] = {
          F.syncThrowable(client.rawClient.removeImageCmd(imageName).withForce(true).exec()).void.catchAll(_ => F.unit)
        }

        def verifyImageNotPulled: F[Throwable, NotFoundException] = {
          F.syncThrowable {
            intercept[NotFoundException](client.rawClient.inspectImageCmd(imageName).exec())
          }
        }

        def verifyImagePulled: F[Throwable, Assertion] = {
          F.syncThrowable {
            val inspection = client.rawClient.inspectImageCmd(imageName).exec()
            assert(inspection.getArch == "riscv64")
            assert(inspection.getOs == "linux")
          }
        }

        (for {
          _ <- removeImage()
          _ <- verifyImageNotPulled
          _ <- containerResource.use(_ => verifyImagePulled)
        } yield ()).guarantee(removeImage())
    }

  }

}
