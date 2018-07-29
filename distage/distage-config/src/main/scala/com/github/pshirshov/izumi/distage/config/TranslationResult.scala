package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.SafeType
import com.typesafe.config.ConfigValue

sealed trait TranslationResult

object TranslationResult {

  sealed trait TranslationFailure extends TranslationResult {
    def op: ExecutableOp

    def target: RuntimeDIUniverse.DIKey = op.target

    protected def origin: String = {
      op.origin match {
        case Some(v) =>
          s"${v.origin.toString} ($target)"
        case None =>
          target.toString
      }
    }
  }

  final case class Success(op: ExecutableOp) extends TranslationResult

  final case class MissingConfigValue(op: ExecutableOp, paths: Seq[(ConfigPath, Throwable)]) extends TranslationFailure {
    override def toString: String = {
      import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
      val tried = paths.map {
        case (path, f) =>
          s"${path.toPath}: ${f.getMessage}"
      }.niceList()

      val details = s"""origin: $origin
         |tried: $tried""".stripMargin.shift(4)

      s"Missing config value:\n$details"
    }
  }

  final case class ExtractionFailure(op: ExecutableOp, tpe: SafeType, path: String, config: ConfigValue, f: Throwable) extends TranslationFailure {
    override def toString: String = s"$origin: cannot read $tpe out of $path ==> $config: ${f.getMessage}"
  }

  final case class Failure(op: ExecutableOp, f: Throwable) extends TranslationFailure {
    override def toString: String = s"$origin: unexpected exception: ${f.getMessage}"
  }

}
