package izumi.distage.testkit.docker

import izumi.distage.docker.model.{DockerFailureCause, DockerFailureException}
import org.scalatest.wordspec.AnyWordSpec

final class DockerExceptionTest extends AnyWordSpec {
  "DockerFailureException" should {
    "expose the cause it was constructed with" in {
      val cause = new RuntimeException("boom")
      val exception = DockerFailureException("container failed", DockerFailureCause.Throwed(cause), cause)
      assert(exception.getCause eq cause)
    }

    "have no cause when constructed without one" in {
      assert(DockerFailureException("container failed", DockerFailureCause.Bug).getCause == null)
    }
  }
}
