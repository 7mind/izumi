package izumi.distage.model.definition

import izumi.reflect.{Tag, TagK}

trait LifecycleTagImpl[R] {
  type F[_]
  type A
  implicit def tagFull: Tag[R]
  implicit def tagK: TagK[F]
  implicit def tagA: Tag[A]
}

object LifecycleTagImpl extends LifecycleTagLowPriority {
  @inline def apply[A: LifecycleTagImpl]: LifecycleTagImpl[A] = implicitly

  implicit def resourceTag[R <: Lifecycle[F0, A0]: Tag, F0[_]: TagK, A0: Tag]: LifecycleTagImpl[R with Lifecycle[F0, A0]] { type F[X] = F0[X]; type A = A0 } = {
    new LifecycleTagImpl[R] {
      type F[X] = F0[X]
      type A = A0
      val tagK: TagK[F0] = TagK[F0]
      val tagA: Tag[A0] = Tag[A0]
      val tagFull: Tag[R] = Tag[R]
    }
  }
}
