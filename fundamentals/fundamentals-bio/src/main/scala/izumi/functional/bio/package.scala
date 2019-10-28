package izumi.functional

import izumi.functional.mono.{Clock, Entropy, SyncSafe}

package object bio {
  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]
  object SyncSafe2 {
    def apply[F[_, _] : SyncSafe2]: SyncSafe2[F] = implicitly
  }

  type Clock2[F[_, _]] = Clock[F[Nothing, ?]]
  object Clock2 {
    def apply[F[_, _]: Clock2]: Clock2[F] = implicitly
  }

  type Entropy2[F[_, _]] = Entropy[F[Nothing, ?]]
  object Entropy2 {
    def apply[F[_, _]: Entropy2]: Entropy2[F] = implicitly
  }
}

