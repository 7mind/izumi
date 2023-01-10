package izumi.distage.planning

import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.errors.DIError.{ConflictResolutionFailed, LoopResolutionError}
import izumi.distage.model.exceptions.planning.InjectorFailed

class DIFailureInterpreter(activation: Activation) {
  implicit class DIResultExt[A](result: Either[List[DIError], A]) {
    def getOrThrow: A = {
      result match {
        case Left(errors) =>
          throwOnError(errors)

        case Right(resolved) =>
          resolved
      }
    }
  }

  // TODO: we need to completely get rid of exceptions, this is just some transitional stuff
  def throwOnError(issues: List[DIError]): Nothing = {
    val conflicts = issues.collect { case c: ConflictResolutionFailed => c }
    if (conflicts.nonEmpty) {
      throwOnConflict(conflicts)
    }
    import izumi.fundamentals.platform.strings.IzString.*

    val loops = issues.collect { case e: LoopResolutionError => DIError.formatError(e) }.niceList()
    if (loops.nonEmpty) {
      throw new InjectorFailed(
        s"""Injector failed unexpectedly. List of issues: $loops
       """.stripMargin,
        issues,
      )
    }

    val inconsistencies = issues.collect { case e: DIError.VerificationError => DIError.formatError(e) }.niceList()
    if (inconsistencies.nonEmpty) {
      throw new InjectorFailed(
        s"""Injector failed unexpectedly. List of issues: $loops
       """.stripMargin,
        issues,
      )
    }

    throw new InjectorFailed("BUG: Injector failed and is unable to provide any diagnostics", List.empty)
  }

  protected[this] def throwOnConflict(issues: List[ConflictResolutionFailed]): Nothing = {
    val rawIssues = issues.map(_.error)
    val issueRepr = rawIssues.map(DIError.formatConflict(activation)).mkString("\n", "\n", "")

    throw new InjectorFailed(
      s"""Found multiple instances for a key. There must be exactly one binding for each DIKey. List of issues:$issueRepr
         |
         |You can use named instances: `make[X].named("id")` syntax and `distage.Id` annotation to disambiguate between multiple instances of the same type.
       """.stripMargin,
      rawIssues.map(DIError.ConflictResolutionFailed.apply),
    )
  }
}
