package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

sealed trait TypespaceVerificationIssue

object TypespaceVerificationIssue {

  final case class PrimitiveAdtMember(t: AdtId, members: List[AdtMember]) extends TypespaceVerificationIssue {
    override def toString: String = s"ADT members can't be primitive (TS implementation limit): ${members.mkString(", ")}"
  }

  final case class AmbigiousAdtMember(t: AdtId, types: List[TypeId]) extends TypespaceVerificationIssue {
    override def toString: String = s"ADT hierarchy contains same members (TS implementation limit): ${types.mkString(", ")}"
  }

  final case class DuplicateEnumElements(t: EnumId, members: List[String]) extends TypespaceVerificationIssue {
    override def toString: String = s"Duplicated enumeration members: ${members.mkString(", ")}"
  }

  final case class DuplicateAdtElements(t: AdtId, members: List[String]) extends TypespaceVerificationIssue {
    override def toString: String = s"Duplicated ADT members: ${members.mkString(", ")}"
  }

  final case class NoncapitalizedTypename(t: TypeId) extends TypespaceVerificationIssue {
    override def toString: String = s"All typenames must start with a capital letter: $t"
  }

  final case class ShortName(t: TypeId) extends TypespaceVerificationIssue {
    override def toString: String = s"All typenames be at least 2 characters long: $t"
  }

  final case class ReservedTypenamePrefix(t: TypeId) extends TypespaceVerificationIssue {
    override def toString: String = s"Typenames can't start with reserved runtime prefixes ${TypespaceVerifier.badNames.mkString(",")}: $t"
  }

  final case class CyclicInheritance(t: TypeId) extends TypespaceVerificationIssue {
    override def toString: String = s"Type is involved into cyclic inheritance: $t"
  }

  final case class CyclicUsage(t: TypeId, cycles: Set[TypeId]) extends TypespaceVerificationIssue {
    override def toString: String = s"Cyclic usage disabled due to serialization issues, use opt[T] to break the loop: $t. Cycle caused by: $cycles"
  }

  final case class MissingDependencies(deps: List[MissingDependency]) extends TypespaceVerificationIssue {
    override def toString: String = s"Missing dependencies: ${deps.mkString(", ")}"
  }

  @deprecated("We need to improve design and get rid of this", "2018-12-06")
  final case class VerificationException(message: String) extends TypespaceVerificationIssue {
    override def toString: String = s"Verification failed with exception. Message: $message"
  }
}
