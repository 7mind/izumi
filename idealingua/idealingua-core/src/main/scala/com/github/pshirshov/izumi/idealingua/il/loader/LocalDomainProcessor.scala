package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.fundamentals.platform.files.{IzFiles, IzZip}
import com.github.pshirshov.izumi.idealingua.il.loader.model.LoadedModel
import com.github.pshirshov.izumi.idealingua.il.parser.model.{ParsedDomain, ParsedModel}
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.DomainDefinitionParsed
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILImport

protected[loader] class LocalDomainProcessor(root: Path, classpath: Seq[File], domain: ParsedDomain, domains: Map[DomainId, ParsedDomain], models: Map[Path, ParsedModel]) {

  import LocalModelLoader._


  def postprocess(): DomainDefinitionParsed = {
    val domainResolver: (DomainId) => Option[ParsedDomain] = toDomainResolver(domains.get)
    val modelResolver: (Path) => Option[ParsedModel] = toModelResolver(models.get)

    val imports = domain
      .imports
      .map {
        p =>
          domainResolver(p.id) match {
            case Some(d) =>
              d.did -> new LocalDomainProcessor(root, classpath, d, domains, models).postprocess()

            case None =>
              throw new IDLException(s"Can't find reference $p in classpath nor filesystem while operating within $root")
          }
      }
      .toMap

    val importOps = domain.imports.flatMap {
      i =>
        i.identifiers.map(ILImport(i.id, _))
    }

    val allIncludes = domain.model.includes
      .map(loadModel(modelResolver, _))
      .fold(LoadedModel(domain.model.definitions))(_ ++ _)

    raw.DomainDefinitionParsed(domain.did, allIncludes.definitions ++ importOps, imports)
  }

  private def loadModel(modelResolver: Path => Option[ParsedModel], toInclude: String): LoadedModel = {
    val incPath = Paths.get(toInclude)

    modelResolver(incPath) match {
      case Some(inclusion) =>
        inclusion.includes
          .map(loadModel(modelResolver, _))
          .fold(LoadedModel(inclusion.definitions)) {
            case (acc, m) => acc ++ m
          }

      case None =>
        throw new IDLException(s"Can't find inclusion $incPath in classpath nor filesystem while operating within $root")
    }
  }

  // TODO: decopypaste?
  private def toModelResolver(primary: Path => Option[ParsedModel])(incPath: Path): Option[ParsedModel] = {
    primary(incPath)
      .orElse {
        val fallback = resolveFromCP(incPath, Some("idealingua"))
          .orElse(resolveFromCP(incPath, None))
          .orElse(resolveFromJavaCP(incPath))
          .orElse(resolveFromJars(incPath))

        fallback.map {
          src =>
            parseModels(Map(incPath -> src))(incPath)
        }
      }
  }

  private def toDomainResolver(primary: DomainId => Option[ParsedDomain])(incPath: DomainId): Option[ParsedDomain] = {
    val asPath = toPath(incPath)

    primary(incPath)
      .orElse {
        val fallback = resolveFromCP(asPath, Some("idealingua"))
          .orElse(resolveFromCP(asPath, None))
          .orElse(resolveFromJars(asPath))
          .orElse(resolveFromJavaCP(asPath))

        fallback.map {
          src =>
            val parsed = parseDomains(Map(asPath -> src))
            parsed(incPath)
        }
      }
  }

  private def resolveFromCP(incPath: Path, prefix: Option[String]): Option[String] = {
    val allCandidates = (Seq(root, root.resolve(toPath(domain.did)).getParent).map(_.toFile) ++ classpath)
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
    Option(getClass.getResource(Paths.get("/idealingua/").resolve(incPath).toString))
      .map {
        fallback =>
          IzFiles.readString(new File(fallback.toURI).toPath)
      }
  }

  private def resolveFromJars(incPath: Path): Option[String] = {
    IzZip.findInZips(incPath, classpath)
  }
}
