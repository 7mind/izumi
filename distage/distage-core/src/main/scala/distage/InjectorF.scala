package distage

import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectRunner}

sealed trait InjectorF[F[_], T] {
}

object InjectorF {

//  case class FlatMap[F[_]](module: PlannerInput) extends InjectorF[F, PlannerInput]
  case class Module[F[_]](module: PlannerInput) extends InjectorF[F, PlannerInput] {

    def flatMap[T1](f: Locator => F[InjectorF[F, T1]])(implicit eff: DIEffect[F], tag: TagK[F]): F[InjectorF[F, T1]] = {
      val F = implicitly[DIEffect[F]]
      for {
        m <- F.maybeSuspend[PlannerInput](module)
        inj = Injector().produceF[F](m)
        out <- F.bracket(inj.acquire)(a => inj.release(a)) {
          r =>
            f(inj.extract(r))
        }
      } yield {

        out
      }
    }
  }

  case class End[F[_], T](t: T) extends InjectorF[F, T] {
    def map[T1](f: T => T1)(implicit eff: DIEffect[F], tag: TagK[F]): F[InjectorF[F, T1]] = {
      val F = implicitly[DIEffect[F]]
      F.pure(End(f(t)))
    }
  }

  def module[F[_]](module: PlannerInput) = new Module[F](module)

  def end[F[_], T](value: T) = new End[F, T](value)

  def run[F[_] : DIEffect : DIEffectRunner, T](injectorF: F[InjectorF[F, T]]): InjectorF[F, T] = {
    DIEffectRunner[F].run(injectorF)
  }
}

