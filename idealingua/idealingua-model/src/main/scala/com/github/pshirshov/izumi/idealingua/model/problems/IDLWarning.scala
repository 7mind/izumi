package com.github.pshirshov.izumi.idealingua.model.problems

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

sealed trait IDLWarning

sealed trait TypespaceWarning extends IDLWarning

object TypespaceWarning {
  final case class Message(message: String) extends TypespaceWarning {
    override def toString: String = s"Warning: $message"
  }

  final case class DomainInvolvedIntoCyclicImports(domain: DomainId, loops: Set[Seq[DomainId]]) extends TypespaceWarning {
    override def toString: String = {
      val diag = loops.map(_.mkString("->"))
      s"Warning: domain $domain is involved into cyclic imports, it makes a domain nonportable to languages that do" +
        s" not support cyclic imports, such as Go: ${diag.niceList().shift(2)}"
    }
  }
}
