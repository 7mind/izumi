package distage

import izumi.distage.bootstrap.{BootstrapLocator, CglibBootstrap}
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._

sealed trait InjectorF[F[_], T] {
}

object InjectorF {

  case class FlatMapLocator[F[_], T1](
                                       prev: InjectorF[F, PlannerInput],
                                       f: Locator => InjectorF[F, T1]
                                     ) extends InjectorF[F, T1]

  case class FlatMapValue[F[_], T0, T1](
                                         prev: InjectorF[F, T0],
                                         f: T0 => InjectorF[F, T1]
                                       ) extends InjectorF[F, T1]

  case class Map[F[_], T, T1](
                               prev: InjectorF[F, T],
                               f: T => T1
                             ) extends InjectorF[F, T1]

  case class DeclareModule[F[_]](module: PlannerInput) extends InjectorF[F, PlannerInput] {
    def flatMap[T1](f: Locator => InjectorF[F, T1]): InjectorF[F, T1] = {
      FlatMapLocator(this, f)
    }
  }

  case class UseLocator[F[_], T](t: Locator => T) extends InjectorF[F, T] {
    def map[T1](f: T => T1): InjectorF[F, T1] = {
      UseLocator(locator => f(t(locator)))
    }

    def flatMap[T1](f: T => InjectorF[F, T1]): InjectorF[F, T1] = {
      FlatMapValue(this, f)
    }
  }

  def declare[F[_]](module: PlannerInput): DeclareModule[F] = new DeclareModule[F](module)
  def useLocator[F[_], T](f: Locator => T): UseLocator[F, T] = new UseLocator[F, T](f)
  def use[F[_], T](f: ProviderMagnet[T]): UseLocator[F, T] = new UseLocator[F, T](_.run(f))

  def run[F[_] : DIEffect : TagK, T](
                                      injectorF: InjectorF[F, T],
                                      bootstrap: BootstrapContextModule = CglibBootstrap.cogenBootstrap,
                                      overrides: BootstrapModule = BootstrapModule.empty,
                                    ): F[T] = {
    val F = implicitly[DIEffect[F]]

    def interpret[T1, B](in: Locator, i: InjectorF[F, T1]): F[B] = {
      val out: F[B] = i match {
        case fm: FlatMapLocator[F, PlannerInput]@unchecked =>
          for {
            ms <- F.maybeSuspend(interpret[PlannerInput, PlannerInput](in, fm.prev))
            m <- ms
            loc <- F.maybeSuspend(Injector.inherit(in).produceF[F](m))
            out <- loc.use {
              loc =>
                interpret[PlannerInput, B](loc, fm.f(loc))
            }
          } yield {
            out
          }

        case fm: FlatMapValue[F, T1, B]@unchecked =>
          for {
            p <- F.maybeSuspend(interpret[T1, T1](in, fm.prev))
            v <- p
            i = fm.f.apply(v)
            out <- F.maybeSuspend(interpret[B, B](in, i))
            r <- out
          } yield {
            r
          }

        case m: Map[F, T1, B]@unchecked =>
          for {
            p <- F.maybeSuspend(interpret[T1, T1](in, m.prev).map(m.f))
            out <- p
          } yield {
            out
          }

        case DeclareModule(module) =>
          F.pure(module)

        case u: UseLocator[F, B]@unchecked =>
          F.maybeSuspend(u.t(in))
      }
      out
    }


    val bootstrapDefinition = bootstrap.overridenBy(overrides)
    val bootstrapLocator = new BootstrapLocator(bootstrapDefinition overridenBy new BootstrapModuleDef {
      make[BootstrapModule].fromValue(bootstrapDefinition)
    })
    interpret(bootstrapLocator, injectorF)
  }
}

