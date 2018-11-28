package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.files.{IzFiles, IzZip}

private[loader] class ClasspathFileResolver(root: Path, classpath: Seq[File], cpPrefix: String, candidates: Seq[Path]) {
  def searchClasspath(incPath: Path): Option[String] = {
    val fallback = resolveFromCP(incPath, Some(cpPrefix))
      .orElse(resolveFromCP(incPath, None))
      .orElse(resolveFromJavaCP(incPath))
      .orElse(resolveFromJars(incPath))
    fallback
  }

  private def resolveFromCP(incPath: Path, prefix: Option[String]): Option[String] = {
    val allCandidates = ((root +: candidates).map(_.toFile) ++ classpath)
      .filter(_.isDirectory)
      .flatMap {
        directory =>
          val base = prefix match {
            case None =>
              directory.toPath
            case Some(v) =>
              directory.toPath.resolve(v)

          }

          val candidatePath = base.resolve(incPath)
          val candidates = Seq(candidatePath)
          candidates.map(_.toFile)
      }

    val result = allCandidates
      .find(f => f.exists() && !f.isDirectory)
      .map(path => IzFiles.readString(path.toPath))
    result
  }

  private def resolveFromJavaCP(incPath: Path): Option[String] = {
    Option(getClass.getResource(Paths.get(s"/$cpPrefix/").resolve(incPath).toString))
      .map {
        fallback =>
          IzFiles.readString(new File(fallback.toURI).toPath)
      }
  }

  private def resolveFromJars(incPath: Path): Option[String] = {
    IzZip.findInZips(incPath, classpath)
  }

}
