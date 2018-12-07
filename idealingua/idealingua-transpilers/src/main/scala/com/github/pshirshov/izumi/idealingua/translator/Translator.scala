package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.problems.IDLException
import com.github.pshirshov.izumi.idealingua.model.output.Module

trait Translator {
  def translate(): Seq[Module]

  protected def addRuntime(options: CompilerOptions[_, _], generated: Seq[Module]): Seq[Module] = {
    mergeModules(generated, options.providedRuntime.toSeq.flatMap(_.modules).toSeq)
  }

  protected def mergeModules(m1: Seq[Module], m2: Seq[Module]): Seq[Module] = {
    val combined = m1 ++ m2

    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    val grouped = combined.groupBy(_.id)

    val conflicts = grouped.filter(_._2.size > 1)
    if (conflicts.nonEmpty) {
      throw new IDLException(s"Conflicting modules: ${conflicts.map(kv => s"${kv._1} => ${kv._2.map(_.id).mkString(", ")}").niceList()}")
    }

    combined
  }
}
