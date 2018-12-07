package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.problems.IDLException
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

  def ifWarnings(handler: String => Unit): LoadedModels = {
    collectWarnings match {
      case w if w.nonEmpty =>
        handler(s"Warnings: ${w.niceList()}")
        this
      case _ =>
        this
    }
  }

  def ifFailed(handler: String => Unit): LoadedModels = {
    collectFailures match {
      case f if f.nonEmpty =>
        handler(s"Verification failed: ${f.niceList()}")
      case _ =>
    }

    val duplicates = successful.map(s => s.typespace.domain.id -> s.path).groupBy(_._1).filter(_._2.size > 1)
    if (duplicates.nonEmpty) {
      val messages = duplicates.map(d => s"${d._1}:  ${d._2.niceList().shift(2)}")
      handler(s"Duplicate domain ids: ${messages.niceList()}")
    }

    this
  }

  def throwIfFailed(): LoadedModels = ifFailed(message => throw new IDLException(message))

  def collectFailures: Seq[String] = {
    loaded.collect({ case f: Failure => f })
      .map {
        case ParsingFailed(path, message) =>
          s"Parsing phase (0) failed on $path: $message"
        case f: ResolutionFailed =>
          s"Typespace reference resolution phase (1) failed on ${f.domain} (${f.path}):\n${f.issues.mkString("\n").shift(2)}"
        case f: TyperFailed =>
          s"Typing phase (2) failed on ${f.domain} (${f.path}):\n${f.issues.issues.mkString("\n").shift(2)}"
        case f: VerificationFailed =>
          s"Typespace verification phase (3) failed on ${f.domain} (${f.path}):\n${f.issues.issues.mkString("\n").shift(2)}"
      }
  }

  private def collectWarnings: Seq[String] = {
    loaded
      .collect({ case f: DiagnosableFailure => f.warnings })
      .map {
        m => m.toString()
      }
  }
}

object LoadedModels {
  def apply(loaded: Seq[LoadedDomain]): LoadedModels = new LoadedModels(loaded)
}
