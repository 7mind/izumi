package izumi.functional.lifecycle

import izumi.functional.bio.data.{Morphism2, RestoreInterruption2}
import izumi.functional.bio.{Functor2, IO2, Primitives2, Ref2}

private[lifecycle] object LifecycleMethodImpls {
  @inline final def mapImpl[F[+_, +_], E, A, B](self: Lifecycle[F, E, A])(f: A => B)(implicit F: Functor2[F]): Lifecycle[F, E, B] = {
    new Lifecycle[F, E, B] {
      type InnerResource = self.InnerResource

      override def acquire: F[E, InnerResource] = self.acquire

      override def release(resource: InnerResource): F[Nothing, Unit] = self.release(resource)

      override def extract[C >: B](resource: InnerResource): Either[F[E, C], C] =
        self.extract[A](resource) match {
          case Left(effect) => Left(F.map(effect)(f))
          case Right(value) => Right(f(value))
        }
    }
  }

  @inline final def flatMapImpl[F[+_, +_], E, A, B](
    self: Lifecycle[F, E, A]
  )(f: A => Lifecycle[F, E, B]
  )(implicit F: IO2[F], P: Primitives2[F]
  ): Lifecycle[F, E, B] = {
    new Lifecycle[F, E, B] {
      override type InnerResource = Ref2[F, List[() => F[Nothing, Unit]]]

      private def useAppendFinalizer[T, U](finalizers: InnerResource)(lifecycle: Lifecycle[F, E, T])(use: lifecycle.InnerResource => F[E, U]): F[E, U] = {
        F.uninterruptibleExcept[E, U] {
          restore =>
            F.flatMap[E, lifecycle.InnerResource, U](lifecycle.acquire) {
              a =>
                F.flatMap[E, Unit, U](
                  finalizers.update_(((() => lifecycle.release(a)) :: _))
                )(_ => restore(use(a)))
            }
        }
      }

      override def acquire: F[E, InnerResource] = {
        P.mkRef(List.empty[() => F[Nothing, Unit]])
      }

      override def release(finalizers: InnerResource): F[Nothing, Unit] = {
        F.flatMap[Nothing, List[() => F[Nothing, Unit]], Unit](finalizers.get)(F.traverse_(_)(_.apply()))
      }

      override def extract[C >: B](finalizers: InnerResource): Either[F[E, C], C] = Left {
        useAppendFinalizer(finalizers)(self) {
          (inner1: self.InnerResource) =>
            F.suspendSafe {
              F.flatMap[E, Lifecycle[F, E, B], C](
                self.extract[A](inner1).fold(F.map(_)(f), a => F.pure(f(a)))
              ) {
                (that: Lifecycle[F, E, B]) =>
                  useAppendFinalizer(finalizers)(that) {
                    (inner2: that.InnerResource) =>
                      that.extract[C](inner2).fold(identity, F.pure)
                  }
              }
            }
        }
      }
    }
  }

  @inline final def wrapAcquireImpl[F[+_, +_], E, A, R](
    self: Lifecycle[F, E, A] { type InnerResource = R }
  )(f: (=> F[E, R]) => F[E, R]
  ): Lifecycle[F, E, A] = {
    new Lifecycle[F, E, A] {
      override final type InnerResource = R

      override def acquire: F[E, R] = f(self.acquire)

      override def release(resource: R): F[Nothing, Unit] = self.release(resource)

      override def extract[B >: A](resource: R): Either[F[E, B], B] = self.extract[B](resource)
    }
  }

  @inline final def wrapReleaseImpl[F[+_, +_], E, A, R](
    self: Lifecycle[F, E, A] { type InnerResource = R }
  )(f: (R => F[Nothing, Unit], R) => F[Nothing, Unit]
  ): Lifecycle[F, E, A] = {
    new Lifecycle[F, E, A] {
      override final type InnerResource = R

      override def acquire: F[E, R] = self.acquire

      override def release(resource: R): F[Nothing, Unit] = f(self.release, resource)

      override def extract[B >: A](resource: R): Either[F[E, B], B] = self.extract[B](resource)
    }
  }

  @inline final def redeemImpl[F[+_, +_], E, E2, A, B](
    self: Lifecycle[F, E, A]
  )(failure: E => Lifecycle[F, E2, B],
    success: A => Lifecycle[F, E2, B],
  )(implicit F: IO2[F], P: Primitives2[F]
  ): Lifecycle[F, E2, B] = {
    new Lifecycle[F, E2, B] {
      override type InnerResource = Ref2[F, List[() => F[Nothing, Unit]]]

      private def extractAppendFinalizer[T](finalizers: InnerResource)(lifecycleCtor: () => Lifecycle[F, E2, T]): F[E2, T] = {
        F.uninterruptibleExcept[E2, T] {
          restore =>
            val lifecycle = lifecycleCtor()
            F.flatMap[E2, lifecycle.InnerResource, T](lifecycle.acquire) {
              a =>
                F.flatMap[E2, Unit, T](
                  finalizers.update_(((() => lifecycle.release(a)) :: _))
                )(_ => restore(lifecycle.extract[T](a).fold(identity, F.pure)))
            }
        }
      }

      override def acquire: F[E2, InnerResource] = {
        P.mkRef(List.empty[() => F[Nothing, Unit]])
      }

      override def release(finalizers: InnerResource): F[Nothing, Unit] = {
        F.flatMap[Nothing, List[() => F[Nothing, Unit]], Unit](finalizers.get)(F.traverse_(_)(_.apply()))
      }

      override def extract[C >: B](finalizers: InnerResource): Either[F[E2, C], C] = {
        Left(
          F.redeem[E, A, E2, C](
            extractAppendFinalizer[A](finalizers)(() => self.asInstanceOf[Lifecycle[F, E2, A]]).asInstanceOf[F[E, A]]
          )(
            err = e => extractAppendFinalizer[C](finalizers)(() => (failure(e): Lifecycle[F, E2, B]).asInstanceOf[Lifecycle[F, E2, C]]),
            succ = a => extractAppendFinalizer[C](finalizers)(() => (success(a): Lifecycle[F, E2, B]).asInstanceOf[Lifecycle[F, E2, C]]),
          )
        )
      }
    }
  }

  @inline final def makeUninterruptibleExceptImpl[F[+_, +_], E, A](
    acquire0: RestoreInterruption2[F] => F[E, A]
  )(release0: A => F[Nothing, Unit]
  )(implicit F: IO2[F], P: Primitives2[F]
  ): Lifecycle[F, E, A] = {
    new Lifecycle[F, E, A] {
      override type InnerResource = Ref2[F, List[() => F[Nothing, Unit]]]

      override def acquire: F[E, InnerResource] = {
        P.mkRef(List.empty[() => F[Nothing, Unit]])
      }

      override def release(finalizers: InnerResource): F[Nothing, Unit] = {
        F.flatMap[Nothing, List[() => F[Nothing, Unit]], Unit](finalizers.get)(F.traverse_(_)(_.apply()))
      }

      override def extract[B >: A](finalizers: InnerResource): Either[F[E, B], B] = Left {
        F.uninterruptibleExcept[E, B] {
          restore =>
            F.flatMap[E, A, B](acquire0(restore)) {
              a =>
                F.map[Nothing, Unit, B](
                  finalizers.update_(((() => release0(a)) :: _))
                )(_ => a: B)
            }
        }
      }
    }
  }

  @inline final def mapKImpl[F[+_, +_], G[+_, +_], E, A](self: Lifecycle[F, E, A], f: Morphism2[F, G]): Lifecycle[G, E, A] = {
    new Lifecycle[G, E, A] {
      override type InnerResource = self.InnerResource
      override def acquire: G[E, InnerResource] = f(self.acquire)
      override def release(res: InnerResource): G[Nothing, Unit] = f(self.release(res))
      override def extract[B >: A](res: InnerResource): Either[G[E, B], B] = self.extract[B](res).left.map(fa => f(fa))
    }
  }

}
