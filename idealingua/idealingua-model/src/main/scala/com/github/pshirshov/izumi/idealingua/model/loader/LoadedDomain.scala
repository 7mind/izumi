package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.problems._
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace


sealed trait LoadedDomain

object LoadedDomain {

  final case class Success(path: FSPath, typespace: Typespace, warnings: Vector[IDLWarning]) extends LoadedDomain

  sealed trait Failure extends LoadedDomain

  final case class ParsingFailed(path: FSPath, message: String) extends Failure

  final case class TyperFailed(path: FSPath, domain: DomainId, issues: IDLDiagnostics) extends Failure

  final case class ResolutionFailed(path: FSPath, domain: DomainId, issues: Vector[RefResolverIssue]) extends Failure

  final case class VerificationFailed(path: FSPath, domain: DomainId, issues: IDLDiagnostics) extends Failure

}
