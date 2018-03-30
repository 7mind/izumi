package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.{Path, Paths}

import com.github.pshirshov.izumi.idealingua.il.IL.{ILDef, ILService}
import com.github.pshirshov.izumi.idealingua.il.{LoadedModel, ParsedDomain, ParsedModel}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.parsing
import com.github.pshirshov.izumi.idealingua.model.il.parsing.DomainDefinitionParsed

protected[loader] class LocalDomainProcessor(root: Path, classpath: Seq[File], domain: ParsedDomain, domains: Map[DomainId, ParsedDomain], models: Map[Path, ParsedModel]) {

  import LocalModelLoader._


  def postprocess(): DomainDefinitionParsed = {
    val domainResolver: (DomainId) => Option[ParsedDomain] = toDomainResolver(domains.get)
    val modelResolver: (Path) => Option[ParsedModel] = toModelResolver(models.get)


    val allIncludes = domain.model.includes
      .map(loadModel(modelResolver, _))
      .fold(LoadedModel(domain.model.definitions))(_ ++ _)

    val imports = domain
      .imports
      .map {
        p =>
          domainResolver(p.id) match {
            case Some(d) =>
              d.did.id -> new LocalDomainProcessor(root, classpath, d, domains, models).postprocess()

            case None =>
              throw new IDLException(s"Can't find reference $p in classpath nor filesystem while operating within $root")
          }
      }
      .toMap

    val types = allIncludes.definitions.collect({ case d: ILDef => d.v })
    val services = allIncludes.definitions.collect({ case d: ILService => d.v })
    parsing.DomainDefinitionParsed(domain.did.id, types, services, imports)
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
        val fallback = resolveFromCP(incPath, Some("idealingua"), modelExt)
          .orElse(resolveFromCP(incPath, None, modelExt))
          .orElse(resolveFromJars(incPath))
          .orElse(resolveFromJavaCP(incPath))

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
        val fallback = resolveFromCP(asPath, Some("idealingua"), domainExt)
          .orElse(resolveFromCP(asPath, None, domainExt))
          .orElse(resolveFromJars(asPath))
          .orElse(resolveFromJavaCP(asPath))

        fallback.map {
          src =>
            val parsed = parseDomains(Map(asPath -> src))
            parsed(incPath)
        }
      }
  }

  private def resolveFromJars(incPath: Path): Option[String] = {
    classpath
      .filter(_.isFile)
      .find(f => false) // TODO: support jars!
      .map(path => readFile(path.toPath))
  }

  private def resolveFromCP(incPath: Path, prefix: Option[String], ext: String): Option[String] = {
    val allCandidates = (Seq(root, root.resolve(toPath(domain.did.id)).getParent).map(_.toFile) ++ classpath)
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
      .map(path => readFile(path.toPath))
    result
  }

  private def resolveFromJavaCP(incPath: Path): Option[String] = {
    Option(getClass.getResource(Paths.get("/idealingua/").resolve(incPath).toString))
      .map {
        fallback =>
          readFile(new File(fallback.toURI).toPath)
      }
  }
}
