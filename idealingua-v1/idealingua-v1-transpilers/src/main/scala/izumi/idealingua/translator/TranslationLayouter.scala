package izumi.idealingua.translator

import izumi.idealingua.model.common
import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.output.Module
import izumi.idealingua.model.publishing.{ProjectNamingRule, ProjectVersion}

sealed trait ExtendedModule {
  def module: Module
}

object ExtendedModule {

  case class DomainModule(domain: DomainId, module: Module) extends ExtendedModule

  case class RuntimeModule(module: Module) extends ExtendedModule

}

case class Layouted(emodules: Seq[ExtendedModule]) {
  def modules: Seq[Module] = emodules.map(_.module)
}

trait TranslationLayouter {
  def layout(outputs: Seq[Translated]): Layouted

  protected def toRuntimeModules(options: CompilerOptions[_, _]): Seq[ExtendedModule.RuntimeModule] = {
    for {
      rt <- options.providedRuntime.toSeq
      m <- rt.modules
    } yield {
      ExtendedModule.RuntimeModule(m)
    }
  }

  protected def toDomainModules(generated: Seq[Translated]): Seq[ExtendedModule] = {
    for {
      g <- generated
      m <- g.modules
    } yield {
      ExtendedModule.DomainModule(g.typespace.domain.id, m)
    }
  }

  protected def withRuntime(options: CompilerOptions[_, _], generated: Seq[Translated]): Seq[ExtendedModule] = {
    toRuntimeModules(options) ++ toDomainModules(generated)
  }

  protected def addPrefix(rt: Seq[ExtendedModule], prefix: Seq[String]): Seq[ExtendedModule] = {
    rt.map {
      case ExtendedModule.DomainModule(domain, module) =>
        ExtendedModule.DomainModule(domain, module.copy(id = module.id.copy(path = prefix ++ module.id.path)))
      case ExtendedModule.RuntimeModule(module) =>
        ExtendedModule.RuntimeModule(module.copy(id = module.id.copy(path = prefix ++ module.id.path)))
    }
  }


  protected def renderVersion(version: ProjectVersion): String = {
    val baseVersion = version.version

    if (version.release) {
      baseVersion
    } else {
      s"$baseVersion-${version.snapshotQualifier}"
    }
  }
}

class BaseNamingConvention(rule: ProjectNamingRule) {
  def baseProjectId(did: DomainId): Seq[String] = {
    val pkg = did.toPackage
    baseProjectId(pkg)
  }

  def baseProjectId(pkg: common.Package): Seq[String] = {
    val parts = rule.dropFQNSegments match {
      case Some(v) if v < 0 =>
        pkg.takeRight(-v)
      case Some(0) =>
        pkg
      case None =>
        pkg.lastOption.toSeq
      case Some(v) =>
        pkg.drop(v) match {
          case Nil =>
            pkg.lastOption.toSeq
          case shortened =>
            shortened
        }
    }
    rule.prefix ++ parts ++ rule.postfix
  }

}
