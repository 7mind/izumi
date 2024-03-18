package izumi.functional.bio

trait Parallel2[F[+_, +_]] extends RootBifunctor[F] {
  def InnerF: Monad2[F]

  def parTraverse[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]
  def parTraverseN[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]
  /** [[parTraverseN]] with `maxConcurrent` set to the number of cores, or 2 when on single-core processor */
  def parTraverseNCore[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, List[B]]
  def parTraverse_[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = InnerF.void(parTraverse(l)(f))
  def parTraverseN_[E, A, B](maxConcurrent: Int)(l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = InnerF.void(parTraverseN(maxConcurrent)(l)(f))
  def parTraverseNCore_[E, A, B](l: Iterable[A])(f: A => F[E, B]): F[E, Unit] = InnerF.void(parTraverseNCore(l)(f))

  /**
    * Returns an effect that executes both effects,
    * in parallel, combining their results with the specified `f` function. If
    * either side fails, then the other side will be interrupted.
    */
  def zipWithPar[E, A, B, C](fa: F[E, A], fb: F[E, B])(f: (A, B) => C): F[E, C]

  // defaults
  /**
    * Returns an effect that executes both effects,
    * in parallel, combining their results into a tuple. If either side fails,
    * then the other side will be interrupted.
    */
  def zipPar[E, A, B](fa: F[E, A], fb: F[E, B]): F[E, (A, B)] = zipWithPar(fa, fb)((a, b) => (a, b))

  /**
    * Returns an effect that executes both effects,
    * in parallel, the left effect result is returned. If either side fails,
    * then the other side will be interrupted.
    */
  def zipParLeft[E, A, B](fa: F[E, A], fb: F[E, B]): F[E, A] = zipWithPar(fa, fb)((a, _) => a)

  /**
    * Returns an effect that executes both effects,
    * in parallel, the right effect result is returned. If either side fails,
    * then the other side will be interrupted.
    */
  def zipParRight[E, A, B](fa: F[E, A], fb: F[E, B]): F[E, B] = zipWithPar(fa, fb)((_, b) => b)
}
