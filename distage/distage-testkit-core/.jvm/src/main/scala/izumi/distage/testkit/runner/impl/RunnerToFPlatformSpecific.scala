package izumi.distage.testkit.runner.impl

private[impl] trait RunnerToFPlatformSpecific {
  type PlatformDefaultImpl[F[+_, +_]] = RunnerToF.AsyncImpl[F]
}
