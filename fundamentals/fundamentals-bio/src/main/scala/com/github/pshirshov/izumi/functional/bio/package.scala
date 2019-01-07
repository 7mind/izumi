package com.github.pshirshov.izumi.functional

import com.github.pshirshov.izumi.functional.mono.SyncSafe

package object bio {
  type SyncSafe2[F[_, _]] = SyncSafe[F[Nothing, ?]]

  object SyncSafe2 {
    def apply[F[_, _] : SyncSafe2]: SyncSafe2[F] = implicitly
  }
}

