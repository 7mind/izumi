package izumi.idealingua.model.loader

import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.problems._
import izumi.idealingua.model.typespace.Typespace


sealed trait LoadedDomain

object LoadedDomain {

  final case class Success(path: FSPath, typespace: Typespace, warnings: Vector[IDLWarning]) extends LoadedDomain

  sealed trait Failure extends LoadedDomain

  sealed trait DiagnosableFailure extends Failure {
    def failures: Vector[IDLError]
    def warnings: Vector[IDLWarning]
  }

  final case class ParsingFailed(path: FSPath, message: String) extends Failure

  final case class PostVerificationFailure(issues: IDLDiagnostics) extends Failure

  final case class TyperFailed(path: FSPath, domain: DomainId, issues: IDLDiagnostics) extends DiagnosableFailure {
    override def failures: Vector[IDLError] = issues.issues

    override def warnings: Vector[IDLWarning] = issues.warnings
  }

  final case class ResolutionFailed(path: FSPath, domain: DomainId, issues: Vector[RefResolverIssue]) extends DiagnosableFailure {
    override def failures: Vector[IDLError] = issues

    override def warnings: Vector[IDLWarning] = Vector.empty
  }

  final case class VerificationFailed(path: FSPath, domain: DomainId, issues: IDLDiagnostics) extends DiagnosableFailure {
    override def failures: Vector[IDLError] = issues.issues

    override def warnings: Vector[IDLWarning] = issues.warnings
  }

}
