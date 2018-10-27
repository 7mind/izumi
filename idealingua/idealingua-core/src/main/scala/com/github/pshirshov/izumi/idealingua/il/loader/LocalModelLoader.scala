package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.{Files, Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.il.parser.IDLParser
import com.github.pshirshov.izumi.idealingua.il.parser.model.{ParsedDomain, ParsedModel}
import com.github.pshirshov.izumi.idealingua.model.common.{DomainId, _}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.IDLTyper
import com.github.pshirshov.izumi.idealingua.model.typespace.{FailedTypespace, Typespace, TypespaceImpl, TypespaceVerifier}
import fastparse._


class LocalModelLoader(root: Path, classpath: Seq[File]) extends ModelLoader {

  import LocalModelLoader._


  def load(): Seq[Typespace] = {
    val files = enumerate()
    val domains = parseDomains(files)
    val models = parseModels(files)

    resolve(domains, models)
  }

  def resolve(domains: Map[DomainId, ParsedDomain], models: Map[Path, ParsedModel]): Seq[TypespaceImpl] = {
    val typespaces = domains.map {
      case (_, domain) =>
        new LocalDomainProcessor(root, classpath, domain, domains, models).postprocess()
    }.map {
      d =>
        val domain = new IDLTyper(d).perform()
        new TypespaceImpl(domain)
    }.toSeq

    val issues = typespaces
      .map(ts => FailedTypespace(ts.domain.id, new TypespaceVerifier(ts).verify().toList))
      .filter(_.issues.nonEmpty)

    if (issues.nonEmpty) {
      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      throw new IDLException(s"Verification failed:\n${issues.niceList()}")
    }

    typespaces
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
      .map(f => root.relativize(f) -> IzFiles.readString(f))
      .toMap
  }
}


object LocalModelLoader {
  val domainExt = ".domain"
  val modelExt = ".model"

  def parseModels(files: Map[Path, String]): Map[Path, ParsedModel] = {
    collectSuccess(files, modelExt, IDLParser.parseModel) { (path, parsed) =>
      path
    }
  }

  def parseDomains(files: Map[Path, String]): Map[DomainId, ParsedDomain] = {
    collectSuccess(files, domainExt, IDLParser.parseDomain) { (path, parsed) =>
      parsed.did
    }
  }


  def collectSuccess[T, ID](files: Map[Path, String], ext: TypeName, parser: String => Parsed[T])(mapper: (Path, T) => ID): Map[ID, T] = {
    val parsedValues = files.filter(_._1.getFileName.toString.endsWith(ext))
      .mapValues(s => {
        parser(s)
      })
      .groupBy(_._2.getClass)

    val failures = parsedValues.getOrElse(classOf[Parsed.Failure], Map.empty)
    if (failures.nonEmpty) {
      throw new IDLException(s"Failed to parse definitions:\n${formatFailures(failures)}")
    }

    val success = parsedValues.getOrElse(classOf[Parsed.Success[T]], Map.empty)
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

  def formatFailures[T](failures: Map[Path, Parsed[T]]): String = {
    failures.map(kv => s" -> ${kv._1}: ${kv._2}").mkString("\n")
  }


}
