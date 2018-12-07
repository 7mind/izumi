package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class LoadedModels(loaded: Seq[LoadedDomain]) {

  import LoadedDomain._

  def map(f: LoadedDomain => LoadedDomain): LoadedModels = LoadedModels(loaded.map(f))

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
        case f: ResolutionFailed =>
          s"Domain ${f.domain} failed to resolve external references (${f.path}):\n${f.issues.mkString("\n").shift(2)}"
        case f: TyperFailed =>
          s"Typer failed on ${f.domain} (${f.path}):\n${f.issues.mkString("\n").shift(2)}"
        case f: VerificationFailed =>
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

object LoadedModels {
  def apply(loaded: Seq[LoadedDomain]): LoadedModels = new LoadedModels(loaded)
}
