package com.github.pshirshov.izumi.fundamentals.platform

package object time {
  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]

  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }
}
