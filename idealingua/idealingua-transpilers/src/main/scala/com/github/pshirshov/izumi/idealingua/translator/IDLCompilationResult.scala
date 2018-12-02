package com.github.pshirshov.izumi.idealingua.translator

import java.nio.file.Path

sealed trait IDLCompilationResult

object IDLCompilationResult {
  final case class Success(target: Path, paths: Seq[Path]) extends IDLCompilationResult

  final case class Failure() extends IDLCompilationResult
}
