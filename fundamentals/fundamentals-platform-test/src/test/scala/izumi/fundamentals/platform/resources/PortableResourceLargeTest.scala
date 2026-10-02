package izumi.fundamentals.platform.resources

import org.scalatest.wordspec.AnyWordSpec

class PortableResourceLargeTest extends AnyWordSpec {
  "PortableResource.embedResources" should {
    "embed a resource larger than the 64KB JVM constant-pool limit" in {
      val resources = PortableResource.embedResources("portable-large")

      val key = resources.keys.find(_.contains("large-resource.txt"))
      assert(key.isDefined, "Should contain large-resource.txt")

      val content = resources(key.get)
      assert(content.getBytes("UTF-8").length > 65535, "Content should exceed the 64KB limit")
      assert(content.startsWith("Line 00000:"))
      assert(content.contains("Line 01199:"))
    }
  }
}
