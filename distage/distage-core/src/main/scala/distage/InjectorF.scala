package distage

import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectRunner}

sealed trait InjectorF[F[_], T] {
}

object InjectorF {

  case class FlatMap[F[_], T1](
                                prev: InjectorF[F, PlannerInput],
                                f: Locator => F[InjectorF[F, T1]]
                              ) extends InjectorF[F, T1]
  case class Map[F[_], T, T1](
                               prev: InjectorF[F, T],
                               f: T => T1
                             ) extends InjectorF[F, T1]

  case class Module[F[_]](module: PlannerInput) extends InjectorF[F, PlannerInput] {

    def flatMap[T1](f: Locator => F[InjectorF[F, T1]])(implicit eff: DIEffect[F]): F[InjectorF[F, T1]] = {
      val F = implicitly[DIEffect[F]]
      F.pure(FlatMap(this, f))

    }
  }

  case class End[F[_], T](t: T) extends InjectorF[F, T] {
    def map[T1](f: T => T1)(implicit eff: DIEffect[F]): F[InjectorF[F, T1]] = {
      val F = implicitly[DIEffect[F]]
      F.pure(End(f(t)))
    }
  }

  def module[F[_]](module: PlannerInput) = new Module[F](module)

  def end[F[_], T](value: T) = new End[F, T](value)




  def run[F[_] : DIEffect : DIEffectRunner : TagK, T](injectorF: F[InjectorF[F, T]]): T = {
    val F = implicitly[DIEffect[F]]

    def interpret[T1, B](i: InjectorF[F, T1]): F[B] = {
      i match {
        case fm: FlatMap[F, Any] =>
          for {
            m <- interpret[PlannerInput, PlannerInput](fm.prev)
            inj = Injector().produceF[F](m)
            out <- F.bracket(inj.acquire)(a => inj.release(a)) {
              r =>
                fm.f(inj.extract(r))
            }
          } yield {
            out.asInstanceOf[B]
          }

        case m: Map[F, Any, Any] =>
          interpret(m.prev).map(m.f).asInstanceOf[F[B]]

        case Module(module) =>
          F.pure(module)
        case End(t) =>
          F.pure(t.asInstanceOf[B])
      }
    }
    val p = injectorF.flatMap[T](interpret)
    DIEffectRunner[F].run(p)
  }
}

