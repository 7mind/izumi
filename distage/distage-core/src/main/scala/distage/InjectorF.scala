package distage

import izumi.distage.bootstrap.{BootstrapLocator, CglibBootstrap}
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._

sealed trait InjectorF[F[_], T] {
}

object InjectorF {

  case class State(bootstrapLocator: Locator)

  case class FMPlan[F[_], T1](
                               input: PlannerInput,
                               in: Option[Locator],
                               f: OrderedPlan => InjectorF[F, T1]
                             ) extends InjectorF[F, T1]


  case class FMProduce[F[_], T1](
                                  plan: OrderedPlan,
                                  in: Option[Locator],
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

  case class DeclareModule[F[_]](module: PlannerInput, in: Option[Locator]) {
    def flatMap[T1](f: OrderedPlan => InjectorF[F, T1]): InjectorF[F, T1] = {
      FMPlan(module, in, f)
    }
  }

  case class CreateSubcontext[F[_]](plan: OrderedPlan, in: Option[Locator]) {
    def flatMap[T1](f: Locator => InjectorF[F, T1]): InjectorF[F, T1] = {
      FMProduce(plan, in, f)
    }
  }


  case class UseLocator[F[_], T](locator: Locator, t: Locator => T) extends InjectorF[F, T] {
    def map[T1](f: T => T1): InjectorF[F, T1] = {
      UseLocator(locator, l => f(t(l)))
    }

    def flatMap[T1](f: T => InjectorF[F, T1]): InjectorF[F, T1] = {
      FlatMapValue(this, f)
    }
  }

  case class UseState[F[_], T](t: State => T) extends InjectorF[F, T] {
    def map[T1](f: T => T1): InjectorF[F, T1] = {
      UseState(locator => f(t(locator)))
    }

    def flatMap[T1](f: T => InjectorF[F, T1]): InjectorF[F, T1] = {
      FlatMapValue(this, f)
    }
  }


  def plan[F[_]](module: PlannerInput): DeclareModule[F] = new DeclareModule[F](module, None)
  def plan[F[_]](module: PlannerInput, in: Locator): DeclareModule[F] = new DeclareModule[F](module, Some(in))

  def produce[F[_]](plan: OrderedPlan): CreateSubcontext[F] = new CreateSubcontext[F](plan, None)
  def produce[F[_]](plan: OrderedPlan, in: Locator): CreateSubcontext[F] = new CreateSubcontext[F](plan, Some(in))

  //def useLocator[F[_], T](f: Locator => T): UseLocator[F, T] = new UseLocator[F, T](f)
  def use[F[_], T](locator: Locator)(f: ProviderMagnet[T]): UseLocator[F, T] = new UseLocator[F, T](locator, _.run(f))

  def raw[F[_], T](f: State => T): UseState[F, T] = new UseState[F, T](f)

  def run[F[_] : DIEffect : TagK, T](
                                      injectorF: InjectorF[F, T],
                                      bootstrap: BootstrapContextModule = CglibBootstrap.cogenBootstrap,
                                      overrides: BootstrapModule = BootstrapModule.empty,
                                    ): F[T] = {
    val F = implicitly[DIEffect[F]]

    def interpret[T1, B](state: State, i: InjectorF[F, T1]): F[B] = {
      val out: F[B] = i match {

        case fm: FMPlan[F, T1]@unchecked =>
          for {
            plan <- F.maybeSuspend {
              Injector.inherit(fm.in.getOrElse(state.bootstrapLocator)).plan(fm.input)
            }
            p <- F.maybeSuspend(interpret[T1, B](state, fm.f(plan)))
            out <- p
          } yield {
            out
          }

        case fm: FMProduce[F, T1]@unchecked =>
          for {
            loc <- F.maybeSuspend {
              Injector.inherit(fm.in.getOrElse(state.bootstrapLocator))
                .produceF[F](fm.plan)
            }
            p <- loc.use {
              newLocator =>
                F.maybeSuspend(interpret[T1, B](state, fm.f(newLocator)))
            }
            out <- p
          } yield {
            out
          }

        case fm: FlatMapValue[F, T1, B]@unchecked =>
          for {
            p <- F.maybeSuspend(interpret[T1, T1](state, fm.prev))
            v <- p
            i = fm.f.apply(v)
            out <- F.maybeSuspend(interpret[B, B](state, i))
            r <- out
          } yield {
            r
          }

        case m: Map[F, T1, B]@unchecked =>
          for {
            p <- F.maybeSuspend(interpret[T1, T1](state, m.prev).map(m.f))
            out <- p
          } yield {
            out
          }

        case u: UseLocator[F, B]@unchecked =>
          for {
            out <- F.maybeSuspend(u.t(u.locator))
          } yield {
            out
          }

        case u: UseState[F, B]@unchecked =>
          F.maybeSuspend(u.t(state))
      }
      out
    }


    val bootstrapDefinition = bootstrap.overridenBy(overrides)
    val bootstrapLocator = new BootstrapLocator(bootstrapDefinition overridenBy new BootstrapModuleDef {
      make[BootstrapModule].fromValue(bootstrapDefinition)
    })
    val state = State(bootstrapLocator)
    interpret(state, injectorF)
  }
}

