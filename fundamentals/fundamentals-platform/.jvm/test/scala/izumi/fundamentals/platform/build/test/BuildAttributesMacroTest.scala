package izumi.fundamentals.platform.build.test

import izumi.fundamentals.platform.build.BuildAttributes
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Paths
import java.time.{LocalDateTime, ZoneOffset}

class BuildAttributesMacroTest extends AnyWordSpec {

  "BuildAttributes macro" should {

    "return java.home at time of compilation" in {
      val javaHome = BuildAttributes.javaHome()
      val currentHome = Option(System.getProperty("java.home"))
      assert(javaHome == currentHome)
    }

    "return java.home at time of compilation using `buildTimeProperty`" in {
      val javaHome = BuildAttributes.buildTimeProperty("java.home")
      val currentHome = Option(System.getProperty("java.home"))
      assert(javaHome == currentHome)
    }

    "find sbt project root" in {
      val rootDir = Paths.get(BuildAttributes.sbtProjectRoot().get).toAbsolutePath
      val cwd = Paths.get(".").toAbsolutePath
      assert(cwd.startsWith(rootDir))
    }

    "return compilation timestamp" in {
      val buildTime = BuildAttributes.buildTimestamp()
      assert(buildTime.isAfter(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)))
      assert(buildTime.isBefore(LocalDateTime.now()))
    }

  }

}
