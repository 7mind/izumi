package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.output.Module

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

//  protected def mergeModules(m1: Seq[Module], m2: Seq[Module]): Seq[Module] = {
//    val combined = m1 ++ m2
//
//    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
//    val grouped = combined.groupBy(_.id)
//
//    val conflicts = grouped.filter(_._2.size > 1)
//    if (conflicts.nonEmpty) {
//      throw new IDLException(s"Conflicting modules: ${conflicts.map(kv => s"${kv._1} => ${kv._2.map(_.id).mkString(", ")}").niceList()}")
//    }
//
//    combined
//  }
}
