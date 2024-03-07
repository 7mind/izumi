package izumi.functional.bio

final class ErrorAccumulatingOpsTestZIO extends ErrorAccumulatingOpsTest[zio.IO] {
  private val runner: UnsafeRun2[zio.IO] = UnsafeRun2.createZIO()

  override implicit def F: Error2[zio.IO] = Root.BIOZIO
  override def unsafeRun[E, A](f: zio.IO[E, A]): Either[E, A] = runner.unsafeRun(f.attempt)
}
