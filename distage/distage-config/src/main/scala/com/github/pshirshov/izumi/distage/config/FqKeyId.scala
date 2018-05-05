package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._


//private case class ConfigPathPart(base: String, maybeQualifier: Option[String]) {
//  def toPath: String = {
//    maybeQualifier match {
//      case Some(qualifier) =>
//        s"$base.%$qualifier"
//
//      case None =>
//        s"$base.%"
//
//    }
//  }
//}
//
//private case class ConfigPath(base: ConfigPathPart, dependency: Option[ConfigPathPart]) {
//  def toPath: String = {
//    dependency match {
//      case Some(d) =>
//        s"${base.toPath}.~${d.toPath}"
//
//      case None =>
//        s"${base.toPath}"
//
//    }
//  }
//}

private case class ConfigPath(parts: Seq[String]) {
  def toPath: String = parts.mkString(".")
}


private case class RequiredConfigEntry(paths: Seq[ConfigPath], targetClass: Class[_], target: DIKey)

private sealed trait TranslationResult

private object TranslationResult {

  final case class Success(op: ExecutableOp) extends TranslationResult

  final case class Failure(f: Throwable) extends TranslationResult

}

