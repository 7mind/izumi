package izumi.idealingua.model.problems

case class IDLDiagnostics(issues: Vector[IDLError], warnings: Vector[IDLWarning]) {
  def ++(other: IDLDiagnostics): IDLDiagnostics = IDLDiagnostics(issues ++ other.issues, warnings ++ other.warnings)
}

object IDLDiagnostics {
  def empty: IDLDiagnostics = IDLDiagnostics(Vector.empty, Vector.empty)

  def apply(issues: Seq[IDLError]): IDLDiagnostics = new IDLDiagnostics(issues.toVector, Vector.empty)

  def apply(issues: Seq[IDLError], warnings: Seq[IDLWarning]): IDLDiagnostics = new IDLDiagnostics(issues.toVector, warnings.toVector)
}
