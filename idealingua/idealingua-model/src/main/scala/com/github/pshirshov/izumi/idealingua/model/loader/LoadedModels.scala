package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.problems.{IDLDiagnostics, IDLException}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class LoadedModels(loaded: Seq[LoadedDomain], diagnostics: IDLDiagnostics) {
  import LoadedDomain._

  def withDiagnostics(postDiag: IDLDiagnostics): LoadedModels = {
    LoadedModels(loaded, postDiag)
  }

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
        this
      case _ =>
        this
    }
  }

  def throwIfFailed(): LoadedModels = ifFailed(message => throw new IDLException(message))

  def collectFailures: Seq[String] = {
    val pf = if (diagnostics.issues.nonEmpty) {
      diagnostics.issues
    } else {
      Seq.empty
    }

    (pf ++ loaded.collect({ case f: Failure => f }))
      .map {
        case ParsingFailed(path, message) =>
          s"Parsing phase (0) failed on $path: $message"
        case f: ResolutionFailed =>
          s"Typespace reference resolution phase (1) failed on ${f.domain} (${f.path}):\n${f.issues.mkString("\n").shift(2)}"
        case f: TyperFailed =>
          s"Typing phase (2) failed on ${f.domain} (${f.path}):\n${f.issues.issues.mkString("\n").shift(2)}"
        case f: VerificationFailed =>
          s"Typespace verification phase (3) failed on ${f.domain} (${f.path}):\n${f.issues.issues.mkString("\n").shift(2)}"
        case PostVerificationFailure(issues) =>
          s"Global verification phase (4) failed:\n${issues.issues.mkString("\n").shift(2)}"

      }
  }

  private def collectWarnings: Seq[String] = {
    (diagnostics.warnings +: loaded.collect({ case f: DiagnosableFailure => f.warnings }))
      .map {
        m => m.toString()
      }
  }
}

object LoadedModels {
  def apply(loaded: Seq[LoadedDomain], diagnostics: IDLDiagnostics): LoadedModels = new LoadedModels(loaded, diagnostics)
}
