package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.typespace.{Typespace, TypespaceVerificationIssue}


sealed trait LoadedDomain

object LoadedDomain {

  sealed trait Failure extends LoadedDomain

  final case class Success(path: FSPath, typespace: Typespace) extends LoadedDomain

  final case class ParsingFailed(path: FSPath, message: String) extends Failure

  final case class TyperFailed(path: FSPath, domain: DomainId, issues: List[TyperIssue]) extends Failure

  final case class VerificationFailed(path: FSPath, domain: DomainId, issues: List[TypespaceVerificationIssue]) extends Failure

  final case class ResolutionFailed(path: FSPath, domain: DomainId, issues: List[RefResolverIssue]) extends Failure

}
