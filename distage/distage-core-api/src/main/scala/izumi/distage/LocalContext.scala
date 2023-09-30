package izumi.distage

import izumi.distage.model.definition.Identifier
import izumi.functional.lifecycle.Lifecycle
import izumi.distage.model.plan.Plan
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

trait AnyLocalContext[F[_]]

trait LocalContext[F[_], R] extends AnyLocalContext[F] {
  def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R]
  def provideNamed[T: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R]
  def produceRun(): F[R]

  def produce(): Lifecycle[F, R]
  def plan: Plan

  @inline final def provide[T1: Tag, T2: Tag](v1: T1, v2: T2)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    provide(v1).provide(v2)
  }

  @inline final def provide[T1: Tag, T2: Tag, T3: Tag](v1: T1, v2: T2, v3: T3)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    provide(v1, v2).provide(v3)
  }

  @inline final def provide[T1: Tag, T2: Tag, T3: Tag, T4: Tag](v1: T1, v2: T2, v3: T3, v4: T4)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    provide(v1, v2, v3).provide(v4)
  }

  @inline final def provide[T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag](
    v1: T1,
    v2: T2,
    v3: T3,
    v4: T4,
    v5: T5,
  )(implicit pos: CodePositionMaterializer
  ): LocalContext[F, R] = {
    provide(v1, v2, v3, v4).provide(v5)
  }

  @inline final def provide[T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag](
    v1: T1,
    v2: T2,
    v3: T3,
    v4: T4,
    v5: T5,
    v6: T6,
  )(implicit pos: CodePositionMaterializer
  ): LocalContext[F, R] = {
    provide(v1, v2, v3, v4, v5).provide(v6)
  }

  @inline final def provide[T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag](
    v1: T1,
    v2: T2,
    v3: T3,
    v4: T4,
    v5: T5,
    v6: T6,
    v7: T7,
  )(implicit pos: CodePositionMaterializer
  ): LocalContext[F, R] = {
    provide(v1, v2, v3, v4, v5, v6).provide(v7)
  }

  @inline final def provide[T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag](
    v1: T1,
    v2: T2,
    v3: T3,
    v4: T4,
    v5: T5,
    v6: T6,
    v7: T7,
    v8: T8,
  )(implicit pos: CodePositionMaterializer
  ): LocalContext[F, R] = {
    provide(v1, v2, v3, v4, v5, v6, v7).provide(v8)
  }

}
