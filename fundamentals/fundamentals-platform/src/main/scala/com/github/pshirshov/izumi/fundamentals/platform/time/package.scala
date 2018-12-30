package com.github.pshirshov.izumi.fundamentals.platform

package object time {
  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
}
