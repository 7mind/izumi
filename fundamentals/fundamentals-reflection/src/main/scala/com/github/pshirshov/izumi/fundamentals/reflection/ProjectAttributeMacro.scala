package com.github.pshirshov.izumi.fundamentals.reflection

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.util.{Failure, Success}


object ProjectAttributeMacro {
  def extractSbtProjectVersion(): Option[String] = macro readProjectFile

  def readProjectFile(c: blackbox.Context)(): c.Expr[Option[String]] = {
    val srcPath = Paths.get(c.enclosingPosition.source.path)
    import c.universe._

    val result = for {
      root <- projectRoot(srcPath)
      versionFile <- maybeFile(root.resolve("version.sbt"))
      content <- scala.util.Try(new String(Files.readAllBytes(versionFile), StandardCharsets.UTF_8)) match {
        case Failure(exception) =>
          c.warning(c.enclosingPosition, s"Failed to read $versionFile: ${exception.stackTrace}")
          None
        case Success(value) =>
          Some(value)
      }
      parts = content.split('"')
      out <- if (parts.length == 3) {
        Some(parts(1))
      } else {
        c.warning(c.enclosingPosition, s"Unexpected version file content: $content")
        None
      }
    } yield {
      out
    }

    c.Expr[Option[String]](q"$result")
  }

  private def maybeFile(p: Path): Option[Path] = {
    if (p.toFile.exists()) {
      Some(p)
    } else {
      None
    }
  }

  private def projectRoot(cp: Path): Option[Path] = {
    if (cp.resolve("build.sbt").toFile.exists()) {
      Some(cp)
    } else {
      val parent = cp.getParent

      if (parent == null || parent == cp.getRoot) {
        None
      } else {
        projectRoot(parent)
      }
    }

  }

}
