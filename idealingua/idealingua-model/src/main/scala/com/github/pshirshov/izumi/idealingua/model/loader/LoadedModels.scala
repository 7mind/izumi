package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

case class LoadedModels(loaded: Seq[LoadedDomain]) {

  import LoadedDomain._

  def successful: Seq[Success] = {
    loaded.collect {
      case s: Success =>
        s
    }
  }

  def failures: Seq[String] = {
    loaded.collect({case f: Failure => f})
      .map {
        case ParsingFailed(path, message) =>
          s"$path failed to parse: $message"
        case f: TypingFailed =>
          s"Typespace ${f.domain} has failed verification (${f.path}):\n${f.issues.mkString("\n").shift(2)}"
      }
  }

  def throwIfFailed(): LoadedModels = {
    val f = failures
    if (f.nonEmpty) {
      throw new IDLException(s"Verification failed: ${f.niceList()}")
    }

    val duplicates = successful.map(s => s.typespace.domain.id -> s.path).groupBy(_._1).filter(_._2.size > 1)
    if (duplicates.nonEmpty) {
      val messages = duplicates.map(d => s"${d._1}:  ${d._2.niceList().shift(2)}")
      throw new IDLException(s"Duplicate domain ids: ${messages.niceList()}")
    }

    this
  }

}
