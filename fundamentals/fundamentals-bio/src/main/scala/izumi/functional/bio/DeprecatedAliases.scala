package izumi.functional.bio

trait DeprecatedAliases {
  @deprecated("renamed to Functor2", "0.11")
  type BIOFunctor[F[+_, +_]] = Functor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Bifunctor2", "0.11")
  type BIOBifunctor[F[+_, +_]] = Bifunctor3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Applicative2", "0.11")
  type BIOApplicative[F[+_, +_]] = Applicative3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Guarantee2", "0.11")
  type BIOGuarantee[F[+_, +_]] = Guarantee3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to ApplicativeError2", "0.11")
  type BIOApplicativeError[F[+_, +_]] = ApplicativeError3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Monad2", "0.11")
  type BIOMonad[F[+_, +_]] = Monad3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Error2", "0.11")
  type BIOError[F[+_, +_]] = Error3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Bracket2", "0.11")
  type BIOBracket[F[+_, +_]] = Bracket3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Panic2", "0.11")
  type BIOPanic[F[+_, +_]] = Panic3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to IO2", "0.11")
  type BIO[F[+_, +_]] = IO3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Parallel2", "0.11")
  type BIOParallel[F[+_, +_]] = Parallel3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Concurrent2", "0.11")
  type BIOConcurrent[F[+_, +_]] = Concurrent3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Async2", "0.11")
  type BIOAsync[F[+_, +_]] = Async3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Temporal2", "0.11")
  type BIOTemporal[F[+_, +_]] = Temporal3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  @deprecated("renamed to Functor3", "0.11")
  type BIOFunctor3[F[-_, +_, +_]] = Functor3[F]
  @deprecated("renamed to Bifunctor3", "0.11")
  type BIOBifunctor3[F[-_, +_, +_]] = Bifunctor3[F]
  @deprecated("renamed to Applicative3", "0.11")
  type BIOApplicative3[F[-_, +_, +_]] = Applicative3[F]
  @deprecated("renamed to Guarantee3", "0.11")
  type BIOGuarantee3[F[-_, +_, +_]] = Guarantee3[F]
  @deprecated("renamed to ApplicativeError3", "0.11")
  type BIOApplicativeError3[F[-_, +_, +_]] = ApplicativeError3[F]
  @deprecated("renamed to Monad3", "0.11")
  type BIOMonad3[F[-_, +_, +_]] = Monad3[F]
  @deprecated("renamed to Error3", "0.11")
  type BIOError3[F[-_, +_, +_]] = Error3[F]
  @deprecated("renamed to Bracket3", "0.11")
  type BIOBracket3[F[-_, +_, +_]] = Bracket3[F]
  @deprecated("renamed to Panic3", "0.11")
  type BIOPanic3[F[-_, +_, +_]] = Panic3[F]
  @deprecated("renamed to IO3", "0.11")
  type BIO3[F[-_, +_, +_]] = IO3[F]
  @deprecated("renamed to Parallel3", "0.11")
  type BIOParallel3[F[-_, +_, +_]] = Parallel3[F]
  @deprecated("renamed to Concurrent3", "0.11")
  type BIOConcurrent3[F[-_, +_, +_]] = Concurrent3[F]
  @deprecated("renamed to Async3", "0.11")
  type BIOAsync3[F[-_, +_, +_]] = Async3[F]
  @deprecated("renamed to Temporal3", "0.11")
  type BIOTemporal3[F[-_, +_, +_]] = Temporal3[F]

  @deprecated("renamed to Ask3", "0.11")
  type BIOAsk[F[-_, +_, +_]] = Ask3[F]
  @deprecated("renamed to MonadAsk3", "0.11")
  type BIOMonadAsk[F[-_, +_, +_]] = MonadAsk3[F]
  @deprecated("renamed to Profunctor3", "0.11")
  type BIOProfunctor[F[-_, +_, +_]] = Profunctor3[F]
  @deprecated("renamed to Arrow3", "0.11")
  type BIOArrow[F[-_, +_, +_]] = Arrow3[F]
  @deprecated("renamed to ArrowChoice3", "0.11")
  type BIOArrowChoice[F[-_, +_, +_]] = ArrowChoice3[F]
  @deprecated("renamed to Local3", "0.11")
  type BIOLocal[F[-_, +_, +_]] = Local3[F]

  @deprecated("renamed to Fork2", "0.11")
  type BIOFork[F[+_, +_]] = Fork3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]
  @deprecated("renamed to Fork3", "0.11")
  type BIOFork3[F[-_, +_, +_]] = Fork3[F]

  @deprecated("renamed to Fiber2", "0.11")
  type BIOFiber[F[+_, +_], +E, +A] = Fiber3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]], E, A]
  @deprecated("renamed to Fiber3", "0.11")
  type BIOFiber3[F[-_, +_, +_], +E, +A] = Fiber3[F, E, A]
  @deprecated("renamed to Fiber3", "0.11")
  lazy val BIOFiber3 = Fiber3

  @deprecated("renamed to Ref2", "0.11")
  type BIORef[F[+_, +_], A] = Ref2[F, A]
  @deprecated("renamed to Ref2", "0.11")
  lazy val BIORef = Ref2
  @deprecated("renamed to Ref3", "0.11")
  type BIORef3[F[-_, +_, +_], A] = Ref3[F, A]

  @deprecated("renamed to Promise2", "0.11")
  type BIOPromise[F[+_, +_], E, A] = Promise2[F, E, A]
  @deprecated("renamed to Promise2", "0.11")
  lazy val BIOPromise = Promise2
  @deprecated("renamed to Promise3", "0.11")
  type BIOPromise3[F[-_, +_, +_], E, A] = Promise3[F, E, A]

  @deprecated("renamed to Latch2", "0.11")
  type BIOLatch[F[+_, +_]] = Latch2[F]
  @deprecated("renamed to Latch3", "0.11")
  type BIOLatch3[F[-_, +_, +_]] = Latch3[F]

  @deprecated("renamed to Semaphore2", "0.11")
  type BIOSemaphore[F[-_, +_, +_]] = Semaphore2[F[Any, +?, +?]]
  @deprecated("renamed to Semaphore2", "0.11")
  lazy val BIOSemaphore = Semaphore2
  @deprecated("renamed to Semaphore3", "0.11")
  type BIOSemaphore3[F[-_, +_, +_]] = Semaphore3[F]

  @deprecated("renamed to Primitives2", "0.11")
  type BIOPrimitives[F[+_, +_]] = Primitives2[F]
  @deprecated("renamed to Primitives2", "0.11")
  lazy val BIOPrimitives = Primitives2
  @deprecated("renamed to Primitives3", "0.11")
  type BIOPrimitives3[F[-_, +_, +_]] = Primitives2[F[Any, +?, +?]]
  @deprecated("renamed to Primitives3", "0.11")
  lazy val BIOPrimitives3 = Primitives3

  @deprecated("renamed to UnsafeRun2", "0.11")
  type BIORunner[F[_, _]] = UnsafeRun2[F]
  @deprecated("renamed to UnsafeRun2", "0.11")
  lazy val BIORunner = UnsafeRun2

  @deprecated("renamed to UnsafeRun3", "0.11")
  type BIORunner3[F[_, _, _]] = UnsafeRun3[F]
  @deprecated("renamed to UnsafeRun3", "0.11")
  lazy val BIORunner3 = UnsafeRun3

  @deprecated("renamed to Error2", "0.11")
  type BIOMonadError[F[+_, +_]] = Error3[Lambda[(`-R`, `+E`, `+A`) => F[E, A]]]

  @deprecated("renamed to Error3", "0.11")
  type BIOMonadError3[FR[-_, +_, +_]] = Error3[FR]

  @deprecated("renamed to Exit", "0.11")
  type BIOExit[+E, +A] = Exit[E, A]
  @deprecated("renamed to Exit", "0.11")
  lazy val BIOExit = Exit

  @deprecated("renamed to TransZio", "0.11")
  type BIOTransZio[F[_, _]] = TransZio[F]
  @deprecated("renamed to TransZio", "0.11")
  lazy val BIOTransZio = TransZio

  @deprecated("renamed to Root", "0.11")
  type BIORoot = Root
  @deprecated("renamed to Root", "0.11")
  lazy val BIORoot = Root
}
