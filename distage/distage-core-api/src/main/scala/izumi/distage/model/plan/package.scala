package izumi.distage.model

package object plan {
  @deprecated("GCMode has been renamed to `Roots`", "old name will be deleted in 1.1.1")
  type GCMode = Roots
  @deprecated("GCMode has been renamed to `Roots`", "old name will be deleted in 1.1.1")
  lazy val GCMode = Roots
}
