package izumi.functional.lifecycle

import izumi.functional.quasi.{QuasiFunctor, QuasiIO, QuasiPrimitives, QuasiRef}

import java.util.concurrent.atomic.AtomicReference

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

      private[this] def bracketAppendFinalizer[a, b](finalizers: InnerResource)(lifecycle: Lifecycle[F, a])(use: lifecycle.InnerResource => F[b]): F[b] = {
        F.bracket(
          acquire = lifecycle.acquire.flatMap {
            a =>
              finalizers.update((() => lifecycle.release(a)) :: _).map(_ => a)
          }
        )(release = _ => F.unit)(
          use = use
        )
      }

      override def acquire: F[InnerResource] = {
        F.mkRef(Nil)
      }

      override def release(finalizers: InnerResource): F[Unit] = {
        finalizers.get.flatMap(F.traverse_(_)(_.apply()))
      }

      override def extract[C >: B](finalizers: InnerResource): Either[F[C], C] = Left {
        bracketAppendFinalizer(finalizers)(self) {
          (inner1: self.InnerResource) =>
            F.suspendF {
              self.extract(inner1).fold(_.map(f), F pure f(_)).flatMap {
                (that: Lifecycle[F, B]) =>
                  bracketAppendFinalizer(finalizers)(that) {
                    (inner2: that.InnerResource) =>
                      that.extract[C](inner2).fold(identity, F.pure)
                  }
              }
            }
        }
      }
    }
  }

  @inline final def evalMapImpl[F[_], A, B](self: Lifecycle[F, A])(f: A => F[B])(implicit F: QuasiPrimitives[F]): Lifecycle[F, B] = {
    flatMapImpl(self)(a => Lifecycle.liftF(f(a)))
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
      override type InnerResource = AtomicReference[List[() => F[Unit]]]

      private[this] def extractAppendFinalizer[a](finalizers: InnerResource)(lifecycleCtor: () => Lifecycle[F, a]): F[a] = {
        F.bracket(
          acquire = {
            val lifecycle = lifecycleCtor()
            lifecycle.acquire.flatMap {
              a =>
                F.maybeSuspend {
                  // can't use `.updateAndGet` because of Scala.js
                  var oldValue = finalizers.get()
                  while (!finalizers.compareAndSet(oldValue, (() => lifecycle.release(a)) :: oldValue)) {
                    oldValue = finalizers.get()
                  }
                  val doExtract: () => F[a] = {
                    () => lifecycle.extract[a](a).fold(identity, F.pure)
                  }
                  doExtract
                }
            }
          }
        )(release = _ => F.unit)(
          use = doExtract => doExtract()
        )
      }

      override def acquire: F[InnerResource] = {
        F.maybeSuspend(new AtomicReference(Nil))
      }

      override def release(finalizers: InnerResource): F[Unit] = {
        F.suspendF(F.traverse_(finalizers.get())(_.apply()))
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
}
