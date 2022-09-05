package izumi.distage.model.definition

import izumi.functional.bio.{Applicative2, F, IO2, Panic2, Primitives2, RefM2, TypedError}

final class LifecycleAggregator[F[+_, +_], E](
  finalizers: RefM2[F, List[(LifecycleAggregator[F, E]#Key, F[E, Unit])]]
) {

  def acquire[R](resource: Lifecycle[F[E, _], R])(implicit F: IO2[F]): F[E, R] = {
    acquireKey(resource).map(_._1)
  }

  def acquireKey[R](resource: Lifecycle[F[E, _], R])(implicit F: IO2[F]): F[E, (R, Key)] = {
    F.uninterruptibleExcept {
      restore =>
        for {
          inner <- resource.acquire
          key <- F.sync(new Key)
          _ <- finalizers.update_ {
            fins =>
              val finalizer = {
                // suspend
                F.sync(resource.release(inner)).flatten
              }
              F.pure((key -> finalizer) :: fins)
          }
          outer <- restore(resource.extract(inner).fold(identity, F.pure))
        } yield (outer, key)
    }
  }

  def release(key: Key)(implicit F: Applicative2[F]): F[E, Unit] = {
    finalizers.modify {
      map =>
        map.find(_._1 == key) match {
          case Some((_, release)) => release.map(_ -> map.filter(_._1 != key))
          case None => F.pure(() -> map)
        }
    }
  }

  def releaseAll()(implicit F: Panic2[F]): F[Nothing, Unit] = {
    for {
      finalizers <- finalizers.modify(m => F.pure(m, List.empty))
      _ <- finalizers.iterator
        .map(_._2)
        .foldLeft(F.unit) {
          // use `guarantee` to make all finalizers execute even if previous finalizer failed
          _ guarantee _.catchAll {
            case e: Throwable => F.terminate(e)
            case e => F.terminate(TypedError(e))
          }
        }
    } yield ()
  }

  final class Key

}

object LifecycleAggregator {
  def make[F[+_, +_]: Panic2: Primitives2]: Lifecycle[F[Throwable, _], LifecycleAggregator[F, Throwable]] = {
    Lifecycle.make(makeImpl[F, Throwable])(_.releaseAll())
  }

  def makeGeneric[F[+_, +_]: Panic2: Primitives2, E]: Lifecycle[F[E, _], LifecycleAggregator[F, E]] = {
    Lifecycle.make(makeImpl[F, E])(_.releaseAll())
  }

  private[this] def makeImpl[F[+_, +_]: Panic2: Primitives2, E]: F[Nothing, LifecycleAggregator[F, E]] = {
    for {
      finalizers <- F.mkRefM(List.empty[(LifecycleAggregator[F, E]#Key, F[E, Unit])])
    } yield new LifecycleAggregator[F, E](finalizers)
  }
}
