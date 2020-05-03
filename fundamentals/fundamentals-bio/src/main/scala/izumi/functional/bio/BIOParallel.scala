package izumi.functional.bio

trait BIOParallel[F[-_, +_, +_]] extends BIORoot {
  val InnerF: BIOFunctor3[F]

  def parTraverseN[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]
  def parTraverse[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, List[B]]

  /**
    * Returns an effect that executes both effects,
    * in parallel, combining their results with the specified `f` function. If
    * either side fails, then the other side will be interrupted.
    */
  def zipWithPar[R, E, A, R1 <: R, E1 >: E, B, C](fa: F[R, E, A], fb: F[R1, E1, B])(f: (A, B) => C): F[R1, E1, C]

  // defaults
  def parTraverse_[R, E, A, B](l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = InnerF.void(parTraverse(l)(f))
  def parTraverseN_[R, E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[R, E, B]): F[R, E, Unit] = InnerF.void(parTraverseN(maxConcurrent)(l)(f))

  /**
    * Returns an effect that executes both effects,
    * in parallel, combining their results into a tuple. If either side fails,
    * then the other side will be interrupted.
    */
  final def zipPar[R, E, A, R1 <: R, E1 >: E, B](fa: F[R, E, A], fb: F[R1, E1, B]): F[R1, E1, (A, B)] = zipWithPar(fa, fb)((a, b) => (a, b))

  /**
    * Returns an effect that executes both effects,
    * in parallel, the left effect result is returned. If either side fails,
    * then the other side will be interrupted.
    */
  final def zipParLeft[R, E, A, R1 <: R, E1 >: E, B](fa: F[R, E, A], fb: F[R1, E1, B]): F[R1, E1, A] = zipWithPar(fa, fb)((a, _) => a)

  /**
    * Returns an effect that executes both effects,
    * in parallel, the right effect result is returned. If either side fails,
    * then the other side will be interrupted.
    */
  final def zipParRight[R, E, A, R1 <: R, E1 >: E, B](fa: F[R, E, A], fb: F[R1, E1, B]): F[R1, E1, B] = zipWithPar(fa, fb)((_, b) => b)

}
