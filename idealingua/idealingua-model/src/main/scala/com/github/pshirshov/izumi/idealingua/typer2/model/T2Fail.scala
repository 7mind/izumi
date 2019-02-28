package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.UnresolvedName

sealed trait T2Fail

object T2Fail {

  case class CircularDependenciesDetected(loops: List[Set[UnresolvedName]]) extends T2Fail

  case class ConflictingNames(conflicts: Set[UnresolvedName]) extends T2Fail

  case class UnexpectedException(exception: Throwable) extends T2Fail


  sealed trait InterpretationFail extends T2Fail

  case class DependencyMissing(missing: Set[UnresolvedName], blocked: UnresolvedName) extends InterpretationFail
  case class SingleDeclaredType(issue: RawTypeDef.DeclaredType) extends InterpretationFail
}

sealed trait T2Warn

object T2Warn {

}
