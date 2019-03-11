package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.problems.{IDLDiagnostics, IDLException, RefResolverIssue}
import com.github.pshirshov.izumi.idealingua.typer2.model.{T2Fail, T2Warn}


class LoadedModels(loaded: Seq[LoadedDomain], diagnostics: IDLDiagnostics) {

  import LoadedDomain._

  def withDiagnostics(postDiag: IDLDiagnostics): LoadedModels = {
    LoadedModels(loaded, postDiag)
  }

  def all: Vector[LoadedDomain] = loaded.toVector

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
          s"Typespace reference resolution phase (1) failed on ${f.domain} (${f.path}): ${f.issues.map(render).niceList().shift(2)}"
        case f: TyperFailed =>
          s"Typing phase (2) failed on ${f.domain} (${f.path}): ${f.issues.map(render).niceList().shift(2)}"
      }
  }

  private def collectWarnings: Seq[String] = {
    val w = loaded.collect {
      case f: Failure =>
        f match {
          case TyperFailed(_, _, _, warnings) =>
            warnings
          case _: ParsingFailed =>
            Seq.empty
          case _: ResolutionFailed =>
            Seq.empty
        }
      case s: Success =>
        s.typespace.warnings
    }

    (diagnostics.warnings +: w)
      .filter(_.nonEmpty)
      .flatten
      .map(_.toString)
  }

  def render(fail: RefResolverIssue): String = fail.toString

  // TODO: not user friendly at all
  def render(fail: T2Fail): String = fail.toString
  def render(fail: T2Warn): String = fail.toString
}

object LoadedModels {
  def apply(loaded: Seq[LoadedDomain], diagnostics: IDLDiagnostics): LoadedModels = new LoadedModels(loaded, diagnostics)
}
