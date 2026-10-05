package izumi.distage.testkit.docker

import izumi.distage.docker.impl.ContainerResource
import org.scalatest.wordspec.AnyWordSpec

final class HooksDigestTest extends AnyWordSpec {
  "ContainerResource.hooksDigest" should {
    "be absent without hooks" in {
      assert(ContainerResource.hooksDigest(Nil).isEmpty)
    }

    "distinguish hook lists whose keys concatenate to the same text" in {
      assert(ContainerResource.hooksDigest(Seq("a\nb")) != ContainerResource.hooksDigest(Seq("a", "b")))
      assert(ContainerResource.hooksDigest(Seq("", "a")) != ContainerResource.hooksDigest(Seq("a", "")))
      assert(ContainerResource.hooksDigest(Seq("")) != ContainerResource.hooksDigest(Seq("", "")))
    }

    "depend on the order of the hooks" in {
      assert(ContainerResource.hooksDigest(Seq("a", "b")) != ContainerResource.hooksDigest(Seq("b", "a")))
    }
  }
}
