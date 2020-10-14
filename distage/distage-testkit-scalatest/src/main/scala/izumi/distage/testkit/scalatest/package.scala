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

  @deprecated("Renamed to Spec1", "1.0")
  type DistageSpecScalatest[F[+_, +_]] = Spec1[F]

  @deprecated("Renamed to Spec2", "1.0")
  type DistageBIOSpecScalatest[F[+_, +_]] = Spec2[F]

  @deprecated("Renamed to Spec3", "1.0")
  type DistageBIOEnvSpecScalatest[F[-_, +_, +_]] = Spec3[F]
}
