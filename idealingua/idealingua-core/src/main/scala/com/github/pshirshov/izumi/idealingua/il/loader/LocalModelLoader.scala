package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.github.pshirshov.izumi.idealingua.il.parser.ILParser
import com.github.pshirshov.izumi.idealingua.il.parser.model.{ParsedDomain, ParsedModel}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinitionTyper
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainId
import com.github.pshirshov.izumi.idealingua.model.typespace.{Typespace, TypespaceImpl, TypespaceVerifier}
import fastparse.all
import fastparse.core.Parsed


class LocalModelLoader(root: Path, classpath: Seq[File]) extends ModelLoader {

  import LocalModelLoader._


  def load(): Seq[Typespace] = {
    val files = enumerate()
    val domains = parseDomains(files)
    val models = parseModels(files)

    domains.map {
      case (_, domain) =>
        new LocalDomainProcessor(root, classpath, domain, domains, models).postprocess()
    }.map {
      d =>
        val domain = new DomainDefinitionTyper(d).convert()
        val typespace = new TypespaceImpl(domain)
        val issues = new TypespaceVerifier(typespace).verify()
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
        if (issues.nonEmpty) {
          throw new IDLException(s"Typespace verification failed:\n${issues.mkString("\n").shift(2)}")
        }
        typespace
    }.toSeq
  }

  def enumerate(): Map[Path, String] = {
    import scala.collection.JavaConverters._

    val file = root.toFile
    if (!file.exists() || !file.isDirectory) {
      return Map.empty
    }

    java.nio.file.Files.walk(root).iterator().asScala
      .filter {
        f => Files.isRegularFile(f) && (f.getFileName.toString.endsWith(modelExt) || f.getFileName.toString.endsWith(domainExt))
      }
      .map(f => root.relativize(f) -> readFile(f))
      .toMap
  }
}


object LocalModelLoader {
  val domainExt = ".domain"
  val modelExt = ".model"

  def readFile(f: Path): String = {
    new String(Files.readAllBytes(f), StandardCharsets.UTF_8)
  }


  def collectSuccess[T, ID](files: Map[Path, String], ext: TypeName, p: all.Parser[T])(mapper: (Path, T) => ID): Map[ID, T] = {
    val parsedValues = files.filter(_._1.getFileName.toString.endsWith(ext))
      .mapValues(s => {
        p.parse(s)
      })
      .groupBy(_._2.getClass)

    val failures = parsedValues.getOrElse(classOf[Parsed.Failure[Char, String]], Map.empty)
    if (failures.nonEmpty) {
      throw new IDLException(s"Failed to parse definitions:\n${formatFailures(failures)}")
    }

    val success = parsedValues.getOrElse(classOf[Parsed.Success[T, Char, String]], Map.empty)
    val pairs = success.toList.collect {
      case (path, Parsed.Success(r, _)) =>
        mapper(path, r) -> r
    }

    val grouped = pairs.groupBy(_._1)
    val duplicates = grouped.filter(_._2.size > 1)
    if (duplicates.nonEmpty) {
      throw new IDLException(s"Duplicate domain ids: $duplicates")
    }
    grouped.map(_._2.head)
  }

  def toPath(id: DomainId): Path = {
    val p = Paths.get(id.toPackage.mkString("/"))
    p.getParent.resolve(s"${p.getFileName.toString}$domainExt")
  }

  def formatFailures[T](failures: Map[Path, Parsed[T, Char, String]]): String = {
    failures.map(kv => s" -> ${kv._1}: ${kv._2}").mkString("\n")
  }

  def parseModels(files: Map[Path, String]): Map[Path, ParsedModel] = {
    collectSuccess(files, modelExt, new ILParser().modelDef) { (path, parsed) =>
      path
    }
  }

  def parseDomains(files: Map[Path, String]): Map[DomainId, ParsedDomain] = {
    collectSuccess(files, domainExt, new ILParser().fullDomainDef) { (path, parsed) =>
      parsed.did.id
    }
  }
}
