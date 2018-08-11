package com.github.pshirshov.izumi.idealingua.runtime.rpc


import scala.language.higherKinds

trait IRTResult[R[_, _]] {
    type Or[E, V] = R[E, V]
    type Just[V] = R[Nothing, V]

    @inline def choice[E, V](v: => Either[E, V]): Or[E, V]

    @inline def just[V](v: => V): Just[V]
    @inline def stop[V](v: => Throwable): Just[V]

    @inline def maybe[V](v: => Either[Throwable, V]): Just[V] = {
        v match {
            case Left(f) =>
                stop(f)
            case Right(r) =>
                just(r)
        }
    }
}
