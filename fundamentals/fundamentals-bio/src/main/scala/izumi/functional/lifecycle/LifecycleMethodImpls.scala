package izumi.functional.lifecycle

import izumi.functional.bio.data.RestoreInterruption1
import izumi.functional.quasi.{QuasiFunctor, QuasiIO, QuasiPrimitives, QuasiRef}

private[lifecycle] object LifecycleMethodImpls {
  @inline final def mapImpl[F[_], A, B](self: Lifecycle[F, A])(f: A => B)(implicit F: QuasiFunctor[F]): Lifecycle[F, B] = {
    new Lifecycle[F, B] {
      type InnerResource = self.InnerResource

      override def acquire: F[InnerResource] = self.acquire

      override def release(resource: InnerResource): F[Unit] = self.release(resource)

      override def extract[C >: B](resource: InnerResource): Either[F[C], C] =
        self.extract(resource) match {
          case Left(effect) => Left(F.map(effect)(f))
          case Right(value) => Right(f(value))
        }
    }
  }

  @inline final def flatMapImpl[F[_], A, B](self: Lifecycle[F, A])(f: A => Lifecycle[F, B])(implicit F: QuasiPrimitives[F]): Lifecycle[F, B] = {
    import QuasiIO.syntax.*
    new Lifecycle[F, B] {
      override type InnerResource = QuasiRef[F, List[() => F[Unit]]]

      private def useAppendFinalizer[T, U](finalizers: InnerResource)(lifecycle: Lifecycle[F, T])(use: lifecycle.InnerResource => F[U]): F[U] = {
        F.uninterruptibleExcept(
          restore =>
            lifecycle.acquire.flatMap {
              a =>
                finalizers
                  .update((() => lifecycle.release(a)) :: _)
                  .flatMap(_ => restore(use(a)))
            }
        )
      }

      override def acquire: F[InnerResource] = {
        F.mkRef(Nil)
      }

      override def release(finalizers: InnerResource): F[Unit] = {
        finalizers.get.flatMap(F.traverse_(_)(_.apply()))
      }

      override def extract[C >: B](finalizers: InnerResource): Either[F[C], C] = Left {
        useAppendFinalizer(finalizers)(self) {
          (inner1: self.InnerResource) =>
            F.suspendF {
              self.extract(inner1).fold(_.map(f), F `pure` f(_)).flatMap {
                (that: Lifecycle[F, B]) =>
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

  @inline final def wrapAcquireImpl[F[_], A](self: Lifecycle[F, A])(f: (=> F[self.InnerResource]) => F[self.InnerResource]): Lifecycle[F, A] = {
    new Lifecycle[F, A] {
      override final type InnerResource = self.InnerResource

      override def acquire: F[InnerResource] = f(self.acquire)

      override def release(resource: InnerResource): F[Unit] = self.release(resource)

      override def extract[B >: A](resource: InnerResource): Either[F[B], B] = self.extract(resource)
    }
  }

  @inline final def wrapReleaseImpl[F[_], A](
    self: Lifecycle[F, A]
  )(f: (self.InnerResource => F[Unit], self.InnerResource) => F[Unit]
  ): Lifecycle[F, A] = {
    new Lifecycle[F, A] {
      override final type InnerResource = self.InnerResource

      override def acquire: F[InnerResource] = self.acquire

      override def release(resource: InnerResource): F[Unit] = f(self.release, resource)

      override def extract[B >: A](resource: InnerResource): Either[F[B], B] = self.extract(resource)
    }
  }

  @inline final def redeemImpl[F[_], A, B](
    self: Lifecycle[F, A]
  )(failure: Throwable => Lifecycle[F, B],
    success: A => Lifecycle[F, B],
  )(implicit F: QuasiIO[F]
  ): Lifecycle[F, B] = {
    import QuasiIO.syntax.*
    new Lifecycle[F, B] {
      override type InnerResource = QuasiRef[F, List[() => F[Unit]]]

      private def extractAppendFinalizer[T](finalizers: InnerResource)(lifecycleCtor: () => Lifecycle[F, T]): F[T] = {
        F.uninterruptibleExcept {
          restore =>
            val lifecycle = lifecycleCtor()
            lifecycle.acquire.flatMap {
              a =>
                finalizers
                  .update((() => lifecycle.release(a)) :: _)
                  .flatMap(_ => restore(lifecycle.extract[T](a).fold(identity, F.pure)))
            }
        }
      }

      override def acquire: F[InnerResource] = {
        F.mkRef(Nil)
      }

      override def release(finalizers: InnerResource): F[Unit] = {
        finalizers.get.flatMap(F.traverse_(_)(_.apply()))
      }

      override def extract[C >: B](finalizers: InnerResource): Either[F[C], C] = {
        Left(
          F.redeem[A, C](extractAppendFinalizer(finalizers)(() => self))(
            failure = e => extractAppendFinalizer(finalizers)(() => failure(e)),
            success = a => extractAppendFinalizer(finalizers)(() => success(a)),
          )
        )
      }
    }
  }

  @inline final def makeUninterruptibleExceptImpl[F[_], A](
    acquire0: RestoreInterruption1[F] => F[A]
  )(release0: A => F[Unit]
  )(implicit F: QuasiPrimitives[F]
  ): Lifecycle[F, A] = {
    import QuasiIO.syntax.*
    new Lifecycle[F, A] {
      override type InnerResource = QuasiRef[F, List[() => F[Unit]]]

      override def acquire: F[InnerResource] = {
        F.mkRef(Nil)
      }

      override def release(finalizers: InnerResource): F[Unit] = {
        finalizers.get.flatMap(F.traverse_(_)(_.apply()))
      }

      override def extract[B >: A](finalizers: InnerResource): Either[F[B], B] = Left {
        F.uninterruptibleExcept {
          restore =>
            acquire0(restore).flatMap {
              a => finalizers.update((() => release0(a)) :: _).map(_ => a)
            }
        }
      }
    }
  }

}
