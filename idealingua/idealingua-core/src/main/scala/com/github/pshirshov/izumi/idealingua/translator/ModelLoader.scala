package com.github.pshirshov.izumi.idealingua.translator

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.github.pshirshov.izumi.idealingua.il.{IL, ILParser, ParsedDomain}
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.DomainDefinition
import fastparse.all
import fastparse.core.Parsed


class ModelLoader(source: Path, classpath: Seq[File]) {
  val parser = new ILParser()

  val domainExt = ".domain"
  val modelExt = ".model"

  type ParsedModel = Seq[IL.Val]
  type InclusionResolver = (Path) => Option[ParsedModel]
  type DomainResolver = (Path) => Option[ParsedDomain]

  def load(): Seq[DomainDefinition] = {
    import scala.collection.JavaConverters._

    val file = source.toFile
    if (!file.exists() || !file.isDirectory) {
      return Seq.empty
    }

    val files = java.nio.file.Files.walk(source).iterator().asScala
      .filter {
        f => Files.isRegularFile(f) && (f.getFileName.toString.endsWith(modelExt) || f.getFileName.toString.endsWith(domainExt))
      }
      .map(f => source.relativize(f) -> readFile(f))
      .toMap

    val domains = collectSuccess(files, domainExt, parser.fullDomainDef)
    val models = collectSuccess(files, modelExt, parser.modelDef)

    domains.map {
      case (_, domain) =>
        postprocess(domain, toResolver(domains.get, parser.fullDomainDef), toResolver(models.get, parser.modelDef))
    }.toSeq
  }

  private def postprocess(domain: ParsedDomain, domainResolver: DomainResolver, modelResolver: InclusionResolver): DomainDefinition = {
    val withIncludes = domain
      .includes
      .foldLeft(domain) {
        case (d, toInclude) =>
          val incPath = Paths.get(toInclude)

          modelResolver(incPath) match {
            case Some(inclusion) =>
              d.extend(inclusion)

            case None =>
              throw new IDLException(s"Can't find inclusion $incPath in classpath nor filesystem while operating within $source")
          }
      }
      .copy(includes = Seq.empty)

    val imports = domain
      .imports.map(s => Paths.get(s))
      .map {
        p =>
          domainResolver(p) match {
            case Some(d) =>
              d.domain.id -> postprocess(d, domainResolver, modelResolver)

            case None =>
              throw new IDLException(s"Can't find reference $p in classpath nor filesystem while operating within $source")
          }
      }
      .toMap

    val withImports = withIncludes
      .copy(imports = Seq.empty, domain = withIncludes.domain.copy(referenced = imports))

    withImports.domain
  }

  private def collectSuccess[T](files: Map[Path, String], ext: TypeName, p: all.Parser[T]): Map[Path, T] = {
    val domains = files.filter(_._1.getFileName.toString.endsWith(ext))
      .mapValues(s => {
        p.parse(s)
      })
      .groupBy(_._2.getClass)

    val failures = domains.getOrElse(classOf[Parsed.Failure[Char, String]], Map.empty)
    if (failures.nonEmpty) {
      throw new IDLException(s"Failed to parse definitions: ${formatFailures(failures)}")
    }

    val success = domains.getOrElse(classOf[Parsed.Success[T, Char, String]], Map.empty)
    success.collect {
      case (path, Parsed.Success(r, _)) =>
        path -> r
    }
  }

  private def toResolver[T](primary: Path => Option[T], p: all.Parser[T])(incPath: Path): Option[T] = {
    primary(incPath)
      .orElse {
        resolveFromCP(incPath)
          .orElse(resolveFromJars(incPath))
          .orElse(resolveFromJavaCP(incPath))
          .map {
            src =>
              collectSuccess(Map(incPath -> src), modelExt, p)(incPath)
          }
      }
  }

  private def resolveFromJars(incPath: Path): Option[String] = {
    classpath
      .filter(_.isFile)
      .find(f => false) // TODO: support jars!
      .map(path => readFile(path.toPath))
  }

  private def resolveFromCP(incPath: Path): Option[String] = {
    classpath
      .filter(_.isDirectory)
      .map(_.toPath.resolve("idealingua").resolve(incPath).toFile)
      .find(_.exists())
      .map(path => readFile(path.toPath))
  }

  private def resolveFromJavaCP(incPath: Path): Option[String] = {
    Option(getClass.getResource(Paths.get("/idealingua/").resolve(incPath).toString))
      .map {
        fallback =>
          readFile(new File(fallback.toURI).toPath)
      }
  }

  private def readFile(f: Path) = {
    new String(Files.readAllBytes(f), StandardCharsets.UTF_8)
  }

  private def formatFailures[T](failures: Map[Path, Parsed[T, Char, String]]) = {
    failures.map(kv => s"${kv._1}: ${kv._2}").mkString("\n")
  }
}
