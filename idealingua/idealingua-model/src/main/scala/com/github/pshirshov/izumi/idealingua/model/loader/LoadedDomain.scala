package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.problems.RefResolverIssue
import com.github.pshirshov.izumi.idealingua.typer2.model.{T2Fail, T2Warn, Typespace2}


sealed trait LoadedDomain

object LoadedDomain {
  final case class Success(typespace: Typespace2) extends LoadedDomain

  sealed trait Failure extends LoadedDomain {
    def path: FSPath
  }
  final case class ParsingFailed(path: FSPath, message: String) extends Failure
  final case class ResolutionFailed(path: FSPath, domain: DomainId, issues: Vector[RefResolverIssue]) extends Failure
  final case class TyperFailed(path: FSPath, domain: DomainId, issues: List[T2Fail], warnings: List[T2Warn]) extends Failure

}
