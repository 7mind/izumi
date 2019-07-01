package com.github.pshirshov.izumi.fundamentals.reflection

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.exceptions.IzThrowable._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.util.{Failure, Success}


object ProjectAttributeMacro {
  def extractSbtProjectGroupId(): Option[String] = macro extractSbtProjectGroupIdMacro

  def extractSbtProjectVersion(): Option[String] = macro extractSbtProjectVersionMacro

  def extractSbtProjectGroupIdMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extract(c, "attributes.sbt", "organization")
  }

  def extractSbtProjectVersionMacro(c: blackbox.Context)(): c.Expr[Option[String]] = {
    extract(c, "version.sbt", "version")

  }

  private def extract(c: blackbox.Context, attrFile: String, attrname: String): c.Expr[Option[String]] = {
    val srcPath = Paths.get(c.enclosingPosition.source.path)
    val result = for {
      root <- projectRoot(srcPath)
      versionFile <- maybeFile(root.resolve(attrFile))
      content <- scala.util.Try(new String(Files.readAllBytes(versionFile), StandardCharsets.UTF_8)) match {
        case Failure(exception) =>
          c.warning(c.enclosingPosition, s"Failed to read $versionFile: ${exception.stackTrace}")
          None
        case Success(value) =>
          Some(value)
      }
      parts = parse(content)
      out <- parts.get(attrname) match {
        case Some(value) =>
          Some(value)
        case None =>
          c.warning(c.enclosingPosition, s"Unexpected version file content: $content")
          None
      }
    } yield {
      out
    }
    import c.universe._

    c.Expr[Option[String]](q"$result")

  }

  def parse(content: String): Map[String, String] = {
    content.split("\n").flatMap {
      c =>
        val parts = c.split("\\:\\=").map(_.trim)
        if (parts.length == 2) {
          val name = parts.head.split(' ').head
          val value = parts.last
          val v = if (value.startsWith("\"") && value.endsWith("\"")) {
            value.substring(1, value.length - 1)
          } else {
            value
          }
          Seq(name -> v)
        } else {
          Seq.empty
        }
    }.toMap
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
