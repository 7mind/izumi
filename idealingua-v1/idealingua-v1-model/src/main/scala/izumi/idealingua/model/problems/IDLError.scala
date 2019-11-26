package izumi.idealingua.model.problems

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{DomainId, TypeId}
import izumi.idealingua.model.il.ast.raw.models.Inclusion
import izumi.idealingua.model.il.ast.typed._
import izumi.idealingua.model.loader.{FSPath, LoadedDomain, ModelParsingResult}
import izumi.idealingua.model.typespace.verification.MissingDependency

sealed trait IDLError

sealed trait PostError extends IDLError

object PostError {
  final case class DuplicatedDomains(paths: Map[DomainId, Seq[FSPath]]) extends PostError {
    override def toString: String = {
      import izumi.fundamentals.platform.strings.IzString._
      val messages = paths.map(d => s"${d._1}:  ${d._2.niceList().shift(2)}")
      s"Duplicate domain ids at different paths: ${messages.niceList()}"
    }
  }

}

sealed trait TyperError extends IDLError

object TyperError {

  final case class TyperException(message: String) extends TyperError {
    override def toString: String = s"Typer failed with exception. Message: $message"
  }

}

sealed trait RefResolverIssue extends IDLError

object RefResolverIssue {

  final case class DuplicatedDomainsDuringLookup(imported: DomainId, paths: List[FSPath]) extends RefResolverIssue {
    override def toString: String = s"$imported: can't lookup domain, multiple domains have that identifier: ${paths.niceList()}"
  }

  final case class InclusionsInOverlay(domain: DomainId, path: FSPath, inclusions: Seq[Inclusion]) extends RefResolverIssue {
    override def toString: String = s"$domain: inclusions are prohibited in overlays at $path: ${inclusions.niceList()} "
  }

  final case class UnparseableInclusion(domain: DomainId, stack: List[Inclusion], failure: ModelParsingResult.Failure) extends RefResolverIssue {
    override def toString: String = s"$domain: can't parse inclusion ${failure.path}, inclusion chain: $domain->${stack.mkString("->")}. Message: ${failure.message}"
  }

  final case class MissingInclusion(domain: DomainId, stack: List[Inclusion], path: Inclusion, diagnostic: List[String]) extends RefResolverIssue {
    override def toString: String = s"$domain: can't find inclusion $path, inclusion chain: $domain->${stack.mkString("->")}. Available: ${diagnostic.niceList()}"
  }

  final case class UnresolvableImport(domain: DomainId, imported: DomainId, failure: LoadedDomain.Failure) extends RefResolverIssue {
    override def toString: String = s"$domain: can't resolve import $imported, problem: $failure"
  }

  final case class MissingImport(domain: DomainId, imported: DomainId, diagnostic: List[String]) extends RefResolverIssue {
    override def toString: String = s"$domain: can't find import $imported. Available: ${diagnostic.niceList()}"
  }

}

sealed trait TypespaceError extends IDLError

object TypespaceError {

  final case class PrimitiveAdtMember(t: AdtId, members: List[AdtMember]) extends TypespaceError {
    override def toString: String = s"ADT members can't be primitive (TS implementation limit): ${members.mkString(", ")}"
  }

  final case class AmbigiousAdtMember(t: AdtId, types: List[TypeId]) extends TypespaceError {
    override def toString: String = s"ADT hierarchy contains same members (TS implementation limit): ${types.mkString(", ")}"
  }

  final case class DuplicateEnumElements(t: EnumId, members: List[String]) extends TypespaceError {
    override def toString: String = s"Duplicated enumeration members: ${members.mkString(", ")}"
  }

  final case class DuplicateAdtElements(t: AdtId, members: List[String]) extends TypespaceError {
    override def toString: String = s"Duplicated ADT members: ${members.mkString(", ")}"
  }

  final case class NoncapitalizedTypename(t: TypeId) extends TypespaceError {
    override def toString: String = s"All typenames must start with a capital letter: $t"
  }

  final case class ShortName(t: TypeId) extends TypespaceError {
    override def toString: String = s"All typenames be at least 2 characters long: $t"
  }

  final case class ReservedTypenamePrefix(t: TypeId, badNames: Set[String]) extends TypespaceError {
    override def toString: String = s"Typenames can't start with reserved runtime prefixes. Type: $t, forbidden prefixes: ${badNames.niceList()}"
  }

  final case class CyclicInheritance(t: TypeId) extends TypespaceError {
    override def toString: String = s"Type is involved into cyclic inheritance: $t"
  }

  final case class CyclicUsage(t: TypeId, cycles: Set[TypeId]) extends TypespaceError {
    override def toString: String = s"Cyclic usage disabled due to serialization issues, use opt[T] to break the loop: $t. Cycle caused by: $cycles"
  }

  final case class MissingDependencies(deps: List[MissingDependency]) extends TypespaceError {
    override def toString: String = s"Missing dependencies: ${deps.mkString(", ")}"
  }

  final case class VerificationException(message: String) extends TypespaceError {
    override def toString: String = s"Verification failed with exception. Message: $message"
  }

  final case class DomainInvolvedIntoCyclicImports(domain: DomainId, loops: Set[Seq[DomainId]], clue: String) extends TypespaceError {
    override def toString: String = {
      val diag = loops.map(_.mkString("->"))
      s"Domain $domain is involved into cyclic imports, $clue: ${diag.niceList().shift(2)}"
    }
  }
}
