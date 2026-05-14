package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.functional.bio.Exit
import izumi.functional.bio.data.{Morphism1, RestoreInterruption1}
import izumi.functional.bio.*
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object unsafe {
  import scala.collection.compat.*

  object EitherSupport {
    import TrySupport.{async1Try, iORunner1Try, io1Try, temporal1Try}

    implicit val async1Either: Async1[Either[Throwable, _]] = {
      new Async1[Either[Throwable, _]] {
        override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Either[Throwable, A] = async1Try.async[A](effect).toEither
        override def fromFuture[A](effect: => Future[A]): Either[Throwable, A] = async1Try.fromFuture[A](effect).toEither
        override def parTraverse[A, B](l: IterableOnce[A])(f: A => Either[Throwable, B]): Either[Throwable, List[B]] = async1Try.parTraverse(l)(f(_).toTry).toEither
        override def parTraverse_[A](l: IterableOnce[A])(f: A => Either[Throwable, Unit]): Either[Throwable, Unit] = async1Try.parTraverse_(l)(f(_).toTry).toEither
        override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Either[Throwable, B]): Either[Throwable, List[B]] =
          async1Try.parTraverseN(n)(l)(f(_).toTry).toEither
        override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => Either[Throwable, Unit]): Either[Throwable, Unit] =
          async1Try.parTraverseN_(n)(l)(f(_).toTry).toEither
      }
    }

    implicit val io1Either: IO1[Either[Throwable, _]] = {
      new IO1[Either[Throwable, _]] {
        override def maybeSuspend[A](eff: => A): Either[Throwable, A] = io1Try.maybeSuspend[A](eff).toEither
        override def maybeSuspendEither[A](eff: => Either[Throwable, A]): Either[Throwable, A] = io1Try.maybeSuspendEither[A](eff).toEither
        override def suspendF[A](effAction: => Either[Throwable, A]): Either[Throwable, A] = maybeSuspendEither(effAction)
        override def pure[A](a: A): Either[Throwable, A] = Right(a)

        override def flatMap[A, B](fa: Either[Throwable, A])(f: A => Either[Throwable, B]): Either[Throwable, B] = io1Try.flatMap(fa.toTry)(f(_).toTry).toEither
        override def map[A, B](fa: Either[Throwable, A])(f: A => B): Either[Throwable, B] = io1Try.map(fa.toTry)(f).toEither

        override def guaranteeOnFailure[A](fa: => Either[Throwable, A])(cleanupOnFailure: Throwable => Either[Throwable, Unit]): Either[Throwable, A] = {
          io1Try.guaranteeOnFailure(fa.toTry)(cleanupOnFailure(_).toTry).toEither
        }
        override def bracketCase[A, B](
          acquire: => Either[Throwable, A]
        )(release: (A, Option[Throwable]) => Either[Throwable, Unit]
        )(use: A => Either[Throwable, B]
        ): Either[Throwable, B] = {
          io1Try.bracketCase[A, B](acquire.toTry)((a: A, o: Option[Throwable]) => release(a, o).toTry)(use(_).toTry).toEither
        }
        override def definitelyRecoverUnsafeIgnoreTrace[A](action: => Either[Throwable, A])(recover: Throwable => Either[Throwable, A]): Either[Throwable, A] = {
          io1Try.definitelyRecoverUnsafeIgnoreTrace(action.toTry)(recover(_).toTry).toEither
        }
        override def definitelyRecoverWithTrace[A](
          action: => Either[Throwable, A]
        )(recoverWithTrace: (Throwable, Exit.Trace[Throwable]) => Either[Throwable, A]
        ): Either[Throwable, A] = {
          io1Try.definitelyRecoverWithTrace(action.toTry)(recoverWithTrace(_, _).toTry).toEither
        }
        override def redeem[A, B](
          action: => Either[Throwable, A]
        )(failure: Throwable => Either[Throwable, B],
          success: A => Either[Throwable, B],
        ): Either[Throwable, B] = {
          io1Try.redeem(action.toTry)(failure(_).toTry, success(_).toTry).toEither
        }
        override def fail[A](t: => Throwable): Either[Throwable, A] = {
          io1Try.fail(t).toEither
        }
        override def tailRecM[A, B](a: A)(f: A => Either[Throwable, Either[A, B]]): Either[Throwable, B] = {
          io1Try.tailRecM[A, B](a)(f(_).toTry).toEither
        }
        override def bracket[A, B](acquire: => Either[Throwable, A])(release: A => Either[Throwable, Unit])(use: A => Either[Throwable, B]): Either[Throwable, B] = {
          io1Try.bracket[A, B](acquire.toTry)(release(_).toTry)(use(_).toTry).toEither
        }
        override def guarantee[A](fa: => Either[Throwable, A])(`finally`: => Either[Throwable, Unit]): Either[Throwable, A] = {
          io1Try.guarantee(fa.toTry)(`finally`.toTry).toEither
        }
        override def traverse[A, B](l: Iterable[A])(f: A => Either[Throwable, B]): Either[Throwable, List[B]] = {
          io1Try.traverse(l)(f(_).toTry).toEither
        }
        override def traverse_[A](l: Iterable[A])(f: A => Either[Throwable, Unit]): Either[Throwable, Unit] = {
          io1Try.traverse_(l)(f(_).toTry).toEither
        }
        override def map2[A, B, C](fa: Either[Throwable, A], fb: => Either[Throwable, B])(f: (A, B) => C): Either[Throwable, C] = {
          io1Try.map2[A, B, C](fa.toTry, fb.toTry)(f).toEither
        }

        override def mkRef[A](a: A): Either[Throwable, Ref0[Either[Throwable, _], A]] = Right(new Ref0[Either[Throwable, _], A] {
          private final val idRef: Ref0[Identity, A] = IO1.io1Identity.mkRef[A](a)
          override def get: Either[Throwable, A] = Right(idRef.get)
          override def set(a: A): Either[Throwable, Unit] = Right(idRef.set(a))
          override def update(f: A => A): Either[Throwable, Unit] = Right(idRef.update(f))
        })

        override def uninterruptibleExcept[A](f: RestoreInterruption1[Either[Throwable, _]] => Either[Throwable, A]): Either[Throwable, A] = {
          f(Morphism1[Either[Throwable, _], Either[Throwable, _]](identity))
        }

        override def tapBothUntyped[A](eff: => Either[Throwable, A])(err: Any => Either[Throwable, Unit], succ: A => Either[Throwable, Unit]): Either[Throwable, A] = {
          io1Try.tapBothUntyped(eff.toTry)(err(_).toTry, succ(_).toTry).toEither
        }

        override def guaranteeOnInterrupt[A](fa: => Either[Throwable, A])(cleanupOnInterrupt: Exit.Trace[Nothing] => Either[Throwable, Unit]): Either[Throwable, A] = {
          io1Try.guaranteeOnInterrupt(fa.toTry)(cleanupOnInterrupt(_).toTry).toEither
        }
      }
    }

    implicit val temporal1Either: Temporal1[Either[Throwable, _]] = new Temporal1[Either[Throwable, _]] {
      override def sleep(duration: FiniteDuration): Either[Throwable, Unit] = temporal1Try.sleep(duration).toEither
    }

    implicit val iORunner1Either: IORunner1[Either[Throwable, _]] = iORunner1Try.contramapK(Morphism1(_.toTry))

    implicit val defaultModuleEither: DefaultModule[Either[Throwable, _]] = DefaultModule(new ModuleDef {
      addImplicit[IO1[Either[Throwable, _]]]
      addImplicit[IORunner1[Either[Throwable, _]]]
      addImplicit[Async1[Either[Throwable, _]]]
      addImplicit[Temporal1[Either[Throwable, _]]]
    })

  }

  object TrySupport {

    implicit val async1Try: Async1[Try] = {
      val id = Async1.async1Identity
      new Async1[Try] {
        override def async[A](effect: (Either[Throwable, A] => Unit) => Unit): Try[A] =
          Try {
            id.async[A](effect)
          }
        override def fromFuture[A](effect: => Future[A]): Try[A] = {
          Try {
            id.fromFuture[A](effect)
          }
        }
        override def parTraverse[A, B](l: IterableOnce[A])(f: A => Try[B]): Try[List[B]] =
          Try {
            id.parTraverse(l)(f(_).get)
          }
        override def parTraverse_[A](l: IterableOnce[A])(f: A => Try[Unit]): Try[Unit] =
          Try {
            id.parTraverse_(l)(f(_).get)
          }
        override def parTraverseN[A, B](n: Int)(l: IterableOnce[A])(f: A => Try[B]): Try[List[B]] =
          Try {
            id.parTraverseN(n)(l)(f(_).get)
          }
        override def parTraverseN_[A](n: Int)(l: IterableOnce[A])(f: A => Try[Unit]): Try[Unit] =
          Try {
            id.parTraverseN_(n)(l)(f(_).get)
          }
      }
    }

    implicit val io1Try: IO1[Try] = {
      val id = IO1.io1Identity
      new IO1[Try[_]] {
        override def maybeSuspend[A](eff: => A): Try[A] = Try(eff)
        override def maybeSuspendEither[A](eff: => Either[Throwable, A]): Try[A] = Try(eff.toTry).flatten
        override def suspendF[A](effAction: => Try[A]): Try[A] = Try(effAction).flatten
        override def pure[A](a: A): Try[A] = scala.util.Success(a)

        override def flatMap[A, B](fa: Try[A])(f: A => Try[B]): Try[B] = fa.flatMap(f)
        override def map[A, B](fa: Try[A])(f: A => B): Try[B] = fa.map(f)

        override def guaranteeOnFailure[A](fa: => Try[A])(cleanupOnFailure: Throwable => Try[Unit]): Try[A] =
          Try {
            id.guaranteeOnFailure(fa.get)(cleanupOnFailure(_).get)
          }

        override def bracketCase[A, B](acquire: => Try[A])(release: (A, Option[Throwable]) => Try[Unit])(use: A => Try[B]): Try[B] =
          Try {
            id.bracketCase[A, B](acquire.get)(release(_, _).get)(use(_).get)
          }

        override def definitelyRecoverUnsafeIgnoreTrace[A](action: => Try[A])(recover: Throwable => Try[A]): Try[A] =
          Try {
            id.definitelyRecoverUnsafeIgnoreTrace(action.get)(recover(_).get)
          }
        override def definitelyRecoverWithTrace[A](action: => Try[A])(recoverWithTrace: (Throwable, Exit.Trace[Throwable]) => Try[A]): Try[A] =
          Try {
            id.definitelyRecoverWithTrace(action.get)(recoverWithTrace(_, _).get)
          }
        override def redeem[A, B](action: => Try[A])(failure: Throwable => Try[B], success: A => Try[B]): Try[B] =
          Try {
            id.redeem[A, B](action.get)(failure(_).get, success(_).get)
          }
        override def fail[A](t: => Throwable): Try[A] =
          Try {
            throw t
          }

        override def tailRecM[A, B](a: A)(f: A => Try[Either[A, B]]): Try[B] =
          Try {
            id.tailRecM[A, B](a)(f(_).get)
          }
        override def bracket[A, B](acquire: => Try[A])(release: A => Try[Unit])(use: A => Try[B]): Try[B] =
          Try {
            id.bracket[A, B](acquire.get)(release(_).get)(use(_).get)
          }
        override def guarantee[A](fa: => Try[A])(`finally`: => Try[Unit]): Try[A] =
          Try {
            id.guarantee(fa.get)(`finally`.get)
          }
        override def traverse[A, B](l: Iterable[A])(f: A => Try[B]): Try[List[B]] =
          Try {
            id.traverse(l)(f(_).get)
          }
        override def traverse_[A](l: Iterable[A])(f: A => Try[Unit]): Try[Unit] =
          Try {
            id.traverse_(l)(f(_).get)
          }

        override def map2[A, B, C](fa: Try[A], fb: => Try[B])(f: (A, B) => C): Try[C] =
          Try {
            id.map2[A, B, C](fa.get, fb.get)(f)
          }

        override def mkRef[A](a: A): Try[Ref0[Try[_], A]] = pure(new Ref0[Try[_], A] {
          private final val idRef: Ref0[Identity, A] = id.mkRef[A](a)
          override def get: Try[A] = pure(idRef.get)
          override def set(a: A): Try[Unit] = pure(idRef.set(a))
          override def update(f: A => A): Try[Unit] = pure(idRef.update(f))
        })

        override def uninterruptibleExcept[A](f: RestoreInterruption1[Try] => Try[A]): Try[A] =
          f(Morphism1[Try, Try](identity))

        override def tapBothUntyped[A](eff: => Try[A])(err: Any => Try[Unit], succ: A => Try[Unit]): Try[A] =
          Try {
            id.tapBothUntyped(eff.get)(err(_).get, succ(_).get)
          }

        override def guaranteeOnInterrupt[A](fa: => Try[A])(cleanupOnInterrupt: Exit.Trace[Nothing] => Try[Unit]): Try[A] = {
          Try {
            id.guaranteeOnInterrupt(fa.get)(cleanupOnInterrupt(_).get)
          }
        }
      }
    }

    implicit val temporal1Try: Temporal1[Try] = new Temporal1[Try] {
      override def sleep(duration: FiniteDuration): Try[Unit] = Try(Temporal1.temporal1Identity.sleep(duration))
    }

    implicit val iORunner1Try: IORunner1[Try] = IORunner1.IdentityImpl.contramapK(Morphism1[Try, Identity](_.get))

    implicit val defaultModuleTry: DefaultModule[Try] = DefaultModule(new ModuleDef {
      addImplicit[IO1[Try]]
      addImplicit[IORunner1[Try]]
      addImplicit[Async1[Try]]
      addImplicit[Temporal1[Try]]
    })

  }

}
