package izumi.distage.testkit

package object scalatest {
  @deprecated("Renamed to AssertZIO", "1.0")
  type AssertIO = AssertZIO
  @deprecated("Renamed to AssertZIO", "1.0")
  lazy val AssertIO: AssertZIO.type = AssertZIO

  @deprecated("Renamed to AssertIO2", "1.0")
  type AssertBIO[F[+_, +_]] = AssertIO2[F]
  @deprecated("Renamed to AssertIO2", "1.0")
  lazy val AssertBIO: AssertIO2.type = AssertIO2
}
