package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.ProjectVersion

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

  protected def baseProjectId(did: DomainId, drop: Option[Int], postfix: Seq[String]): Seq[String] = {
    val pkg = did.toPackage
    val parts = drop.getOrElse(0) match {
      case v if v < 0 =>
        pkg.takeRight(-v)
      case 0 =>
        Seq(pkg.last)
      case v =>
        pkg.drop(v) match {
          case Nil =>
            Seq(pkg.last)
          case shortened =>
            shortened
        }
    }
    parts ++ postfix
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
