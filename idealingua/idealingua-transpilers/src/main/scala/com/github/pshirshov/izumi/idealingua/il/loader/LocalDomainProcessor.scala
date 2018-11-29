//package com.github.pshirshov.izumi.idealingua.il.loader
//
//import java.io.File
//import java.nio.file.{Path, Paths}
//
//import com.github.pshirshov.izumi.idealingua.model.common.DomainId
//import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
//import com.github.pshirshov.izumi.idealingua.model.il.ast.raw
//import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.CompletelyLoadedDomain
//import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILImport
//import com.github.pshirshov.izumi.idealingua.model.loader._
//import com.github.pshirshov.izumi.idealingua.model.parser.{ParsedDomain, ParsedModel}
//
//import scala.collection.mutable
//
//protected[loader] class LocalDomainProcessor(
//                                              root: Path
//                                              , classpath: Seq[File]
//                                              , domain: ParsedDomain
//                                              , unresolved: UnresolvedDomains
//                                              , parser: ModelParser
//                                              , domainExt: String
//                                              , parsed: mutable.HashMap[DomainId, LocalDomainProcessor] = mutable.HashMap.empty
//                                            ) {
//
//
//  def postprocess(path: FSPath): CompletelyLoadedDomain = {
//    val processors = domain
//      .imports
//      .map {
//        p =>
//          p.id -> parsed.getOrElseUpdate(p.id, {
//            domainResolver(p.id) match {
//              case Some(d) =>
//                new LocalDomainProcessor(root, classpath, d, unresolved, parser, domainExt, parsed)
//
//              case None =>
//                throw new IDLException(s"Can't find reference $p in classpath nor filesystem while operating within $root")
//            }
//          })
//      }
//      .toMap
//
//    val imports = processors.mapValues(_.postprocess(path))
//
//    val importOps = domain.imports.flatMap {
//      i =>
//        i.identifiers.map(ILImport(i.id, _))
//    }
//
//    val allIncludes = domain.model.includes
//      .map(loadModel(modelResolver, _))
//      .fold(LoadedModel(domain.model.definitions))(_ ++ _)
//
//    raw.CompletelyLoadedDomain(domain.did, allIncludes.definitions ++ importOps, imports, path)
//  }
//
//  private def loadModel(modelResolver: Path => Option[ParsedModel], toInclude: String): LoadedModel = {
//    val incPath = Paths.get(toInclude)
//
//    modelResolver(incPath) match {
//      case Some(inclusion) =>
//        inclusion.includes
//          .map(loadModel(modelResolver, _))
//          .fold(LoadedModel(inclusion.definitions)) {
//            case (acc, m) => acc ++ m
//          }
//
//      case None =>
//        throw new IDLException(s"Can't find inclusion $incPath in classpath nor filesystem while operating within $root")
//    }
//  }
//
//  private def modelResolver(incPath: Path): Option[ParsedModel] = {
//    val fpath = FSPath(incPath)
//    unresolved.models.results.find(_.path == fpath)
//      .orElse {
//        searchClasspath(incPath)
//          .flatMap {
//            src =>
//              parser
//                .parseModels(Map(fpath -> src))
//                .results.find(_.path == fpath)
//          }
//      }
//      .map(get)
//  }
//
//  private def domainResolver(incPath: DomainId): Option[ParsedDomain] = {
//    val asPath = toPath(incPath)
//    val fpath = FSPath(asPath)
//
//    unresolved.domains.results.find(_.path == fpath)
//      .orElse {
//        searchClasspath(asPath)
//          .flatMap {
//            src =>
//              parser
//                .parseDomains(Map(fpath -> src))
//                .results.find(_.path == asPath)
//          }
//      }
//      .map(get)
//  }
//
//  private def get(r: ModelParsingResult): ParsedModel = {
//    r match {
//      case ModelParsingResult.Success(_, model) =>
//        model
//      case ModelParsingResult.Failure(path, message) =>
//        ???
//    }
//  }
//
//  private def get(r: DomainParsingResult): ParsedDomain = {
//    r match {
//      case DomainParsingResult.Success(_, d) =>
//        d
//      case DomainParsingResult.Failure(path, message) =>
//        ???
//    }
//  }
//
//
//  private def toPath(id: DomainId): Path = {
//    val p = Paths.get(id.toPackage.mkString("/"))
//    p.getParent.resolve(s"${p.getFileName.toString}$domainExt")
//  }
//
//
//  private def searchClasspath(incPath: Path): Option[String] = {
//    new ClasspathFileResolver(root,
//      classpath,
//      "idealingua",
//      Seq(root.resolve(toPath(domain.did)).getParent)
//    )
//      .searchClasspath(incPath)
//  }
//}
//
//
