package distage

import izumi.distage.bootstrap.{BootstrapLocator, CglibBootstrap}
import izumi.distage.model.Locator
import izumi.distage.model.definition.LocatorDef
import izumi.distage.model.effect.DIEffect.syntax._
import izumi.distage.model.effect.{DIEffect, DIEffectRunner}

sealed trait InjectorF[F[_], T] {
}

object InjectorF {

  case class FlatMap[F[_], T1](
                                prev: InjectorF[F, PlannerInput],
                                f: Locator => InjectorF[F, T1]
                              ) extends InjectorF[F, T1]


//  case class FlatMapI[F[_], T1](
//                                prev: InjectorF[F, Injector],
//                                f: Locator => InjectorF[F, T1]
//                              ) extends InjectorF[F, T1]


  case class Map[F[_], T, T1](
                               prev: InjectorF[F, T],
                               f: T => T1
                             ) extends InjectorF[F, T1]

  //  case class Module1[F[_], A1, A2](module: PlannerInput) extends InjectorF[F, PlannerInput] {
  //
  //    def flatMap[T1](f: (A1, A2) => F[InjectorF[F, T1]])(implicit eff: DIEffect[F], dummyImplicit: DummyImplicit): F[InjectorF[F, T1]] = {
  //      ???
  //    }
  //
  //  }
  case class Module[F[_]](module: PlannerInput) extends InjectorF[F, PlannerInput] {
    def flatMap[T1](f: Locator => InjectorF[F, T1]): InjectorF[F, T1] = {
      FlatMap(this, f)
    }
  }

//  case class PreparedInjector[F[_]](module: Injector) extends InjectorF[F, Injector] {
//
//    //    def flatMap[T1](f: ProviderMagnet[T1] => F[InjectorF[F, T1]])(implicit F: DIEffect[F], dummyImplicit: DummyImplicit): F[InjectorF[F, T1]] = {
//    //      ???
//    //    }
//    def flatMap[T1](f: Locator => InjectorF[F, T1]): InjectorF[F, T1] = {
//      FlatMapI(this, f)
//    }
//  }

  case class Return[F[_], T](t: Locator => T) extends InjectorF[F, T] {
    def map[T1](f: T => T1): InjectorF[F, T1] = {
      Return(locator => f(t(locator)))
    }
  }

  def module[F[_]](module: PlannerInput) = new Module[F](module)

  def end[F[_], T](f: Locator => T) = new Return[F, T](f)

  def run[F[_] : DIEffect : DIEffectRunner : TagK, T](injectorF: InjectorF[F, T]): T = {
    val F = implicitly[DIEffect[F]]

    def interpret[T1, B](in: Locator, i: InjectorF[F, T1]): F[B] = {
      i match {
        case fm: FlatMap[F, Any] =>
          for {
            m <- interpret[PlannerInput, PlannerInput](in, fm.prev)
            loc = Injector.inherit(in).produceF[F](m)
            out <- loc.use {
              loc =>
                F.maybeSuspend(interpret(loc, fm.f(loc)))

            }
          } yield {
            out.asInstanceOf[B]
          }

        case m: Map[F, Any, Any] =>
          interpret(in, m.prev).map(m.f).asInstanceOf[F[B]]

        case Module(module) =>
          F.pure(module)

        case Return(t) =>
          F.pure(t(in).asInstanceOf[B])
      }
    }


    DIEffectRunner[F].run(interpret(new BootstrapLocator(CglibBootstrap.cogenBootstrap), injectorF))
  }
}

