package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._


private case class ConfigPath(parts: Seq[String]) {
  def toPath: String = parts.mkString(".")
}


private case class RequiredConfigEntry(paths: Seq[ConfigPath], targetClass: Class[_], target: DIKey) {
  override def toString: String = {
    val allPaths = paths.map(_.toPath).mkString("\n  ")
    s"""class: $targetClass, target: $target
       |
       |$allPaths
     """.stripMargin

  }
}

private sealed trait TranslationResult

private object TranslationResult {

  final case class Success(op: ExecutableOp) extends TranslationResult

  final case class Failure(f: Throwable) extends TranslationResult

}

