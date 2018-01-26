package com.github.pshirshov.izumi.sbt

import sbt.Keys.version
import sbt._
import complete.DefaultParsers._
import sbt.internal.util.ConsoleLogger
import sbt.internal.util.complete.Parser.token
import sbtrelease.Version

object ConvenienceTasksPlugin extends AutoPlugin {
  protected val logger: ConsoleLogger = ConsoleLogger()

  object Keys {
    val addVersionSuffix = inputKey[Unit]("Add a suffix into version defined in version file")
    val preserveTargets = inputKey[Unit]("Preserve 'target' directories")
    val rmDirs = inputKey[Unit]("Recursively remove directories with a name provided")
    val newModule = inputKey[Unit]("Create new empty module layout in current directory")
    val newStub = inputKey[Unit]("Copy stub from stubs/stub into current directory")
  }

  import Keys._

  override def projectSettings = Seq(
    addVersionSuffix := {
      val suffix: String = (token(Space) ~> token(StringBasic, "suffix"))
        .parsed
        .replace('/', '_')
        .replace('.', '_')
        .replace('-', '_')

      val existingVersion = Version(version.value).get
      val newVersion = existingVersion
        .withoutQualifier
        .copy(qualifier = Some(s"-$suffix-SNAPSHOT"))
        .string

      IO.write(file("version.sbt"), s"""version in ThisBuild := "$newVersion"""")
    }
    , preserveTargets := {
      val suffix: String = (token(Space) ~> token(StringBasic, "suffix"))
        .parsed

      val name = "target"
      val pairs = (file(".") ** (DirectoryFilter && new ExactFilter(name))).get.map {
        fn =>
          val withSuffix = fn.toPath.getParent.resolve(s"${fn.getName}.$suffix")
          logger.debug(s"Preserving directory $fn => $withSuffix")
          fn -> withSuffix.toFile
      }
      IO.delete(pairs.map(_._2))
      pairs.foreach {
        case (s, t) =>
          IO.copyDirectory(s, t)
      }
    }
    , rmDirs := {
      val name: String = (token(Space) ~> token(StringBasic, "name"))
        .parsed

      val dirs = (file(".") ** (DirectoryFilter && new ExactFilter(name))).get
      IO.delete(dirs)
    }
    , newModule := {
      val name: String = (token(Space) ~> token(StringBasic, "moduleName"))
        .parsed

      mkDefaultModule(name)
    }
    , newStub := {
      val args = spaceDelimited("<args>").parsed
      val moduleName = args.head
      val stubId = args.tail.headOption.getOrElse("default")
      mkModule(moduleName, stubId)
    }
  )

  private def mkModule(name: String, stubId: String): Unit = {
    val base = file(".").toPath
    val stub = base.resolve("stubs").resolve(stubId).toFile
    if (!stub.exists()) {
      throw new IllegalArgumentException(s"Directory $stub does not exist!")
    }
    val target = base.resolve(name).toFile
    if (target.exists()) {
      throw new IllegalArgumentException(s"Directory $target already exists!")
    }
    target.mkdirs()
    logger.info(s"Copying stub $stub => $target")
    IO.copyDirectory(stub, target)
  }

  private def mkDefaultModule(name: String): Unit = {
    val stub = Seq(
      "src/main/scala"
      , "src/main/java"
      , "src/test/java"
      , "src/test/scala"
    )
    val base = file(".").toPath.resolve(name)
    if (base.toFile.exists()) {
      throw new IllegalArgumentException(s"Directory $base already exists!")
    }

    stub.foreach {
      n =>
        val dir = base.resolve(n)
        val dirOk = dir.toFile.mkdirs()
        val fileOk = dir.resolve(".keep").toFile.createNewFile()
        if (!dirOk || !fileOk) {
          throw new IllegalArgumentException(s"IO failed on $dir")
        }
    }
  }
}
