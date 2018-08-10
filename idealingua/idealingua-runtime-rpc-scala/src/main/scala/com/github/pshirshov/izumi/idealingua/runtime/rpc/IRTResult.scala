package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.language.higherKinds

trait IRTResult[R[_, _]] {
    type Or[E, V] = R[E, V]
    type Just[V] = R[Nothing, V]

    def choice[E, V](v: => Either[E, V]): Or[E, V]
    def just[V](v: => V): Just[V]
}
