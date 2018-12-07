package com.github.pshirshov.izumi.idealingua.model.problems

sealed trait IDLWarning

sealed trait TypespaceWarning extends IDLWarning

object TypespaceWarning {
  final case class Message(message: String) extends TypespaceWarning
}
