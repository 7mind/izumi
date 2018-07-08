package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TypeFull
import com.typesafe.config.Config


sealed trait TranslationResult

object TranslationResult {

  sealed trait TranslationFailure extends TranslationResult {
    def op: ExecutableOp
    def target: RuntimeDIUniverse.DIKey = op.target
  }

  final case class Success(op: ExecutableOp) extends TranslationResult

  final case class MissingConfigValue(op: ExecutableOp, paths: Seq[ConfigPath]) extends TranslationFailure {
    override def toString: String = {
      val tried = paths.mkString("{", "|", "}")
      s"$target: missing config value, tried paths: $tried"
    }
  }

  final case class ExtractionFailure(op: ExecutableOp, tpe: TypeFull, path: String, config: Config, f: Throwable) extends TranslationFailure {
    override def toString: String = s"$target: cannot read $tpe out of $path ==> $config: ${f.getMessage}"
  }

  final case class Failure(op: ExecutableOp, f: Throwable) extends TranslationFailure {
    override def toString: String = s"$target: unexpected exception: ${f.getMessage}"
  }

}
