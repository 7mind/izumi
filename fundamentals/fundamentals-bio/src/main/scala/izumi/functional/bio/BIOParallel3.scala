package izumi.functional.bio

trait BIOParallel3[F[-_, +_, +_]] extends BIORoot {
  val InnerF: BIOMonad3[F]

  def parTraverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]
  def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]

  /**
    * Returns an effect that executes both effects,
    * in parallel, combining their results with the specified `f` function. If
    * either side fails, then the other side will be interrupted.
    */
  def zipWithPar[R, E, A, B, C](fa: F[R, E, A], fb: F[R, E, B])(f: (A, B) => C): F[R, E, C]

  // defaults
  /**
    * Returns an effect that executes both effects,
    * in parallel, combining their results into a tuple. If either side fails,
    * then the other side will be interrupted.
    */
  def zipPar[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, (A, B)] = zipWithPar(fa, fb)((a, b) => (a, b))

  /**
    * Returns an effect that executes both effects,
    * in parallel, the left effect result is returned. If either side fails,
    * then the other side will be interrupted.
    */
  def zipParLeft[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, A] = zipWithPar(fa, fb)((a, _) => a)

  /**
    * Returns an effect that executes both effects,
    * in parallel, the right effect result is returned. If either side fails,
    * then the other side will be interrupted.
    */
  def zipParRight[R, E, A, B](fa: F[R, E, A], fb: F[R, E, B]): F[R, E, B] = zipWithPar(fa, fb)((_, b) => b)

  def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = InnerF.void(parTraverse(l)(f))
  def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = InnerF.void(parTraverseN(maxConcurrent)(l)(f))
}
