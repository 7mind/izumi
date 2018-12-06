package com.github.pshirshov.izumi.idealingua.model.loader

sealed trait TyperIssue

object TyperIssue {
  @deprecated("We need to improve design and get rid of this", "2018-12-06")
  final case class TyperException(message: String) extends TyperIssue {
    override def toString: String = s"Typer failed with exception. Message: $message"
  }
}
