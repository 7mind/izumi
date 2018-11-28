package com.github.pshirshov.izumi.idealingua.il.loader.model

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.typespace.{Issue, Typespace}

sealed trait LoadedDomain

object LoadedDomain {

  sealed trait Failure extends LoadedDomain

  final case class Success(path: FSPath, typespace: Typespace) extends LoadedDomain

  final case class ParsingFailed(path: FSPath, message: String) extends Failure

  final case class TypingFailed(path: FSPath, domain: DomainId, issues: List[Issue]) extends Failure

}
