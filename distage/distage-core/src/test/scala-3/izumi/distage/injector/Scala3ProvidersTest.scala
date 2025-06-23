package izumi.distage.injector

import distage.{Id, ModuleDef, PlannerInput, TagK}
import org.scalatest.wordspec.AnyWordSpec

class Scala3ProvidersTest extends AnyWordSpec with MkInjector {
  "support bindings with function implicit parameters" in {
    final case class Description(description: String)
    final case class X(s: String)

    def makeX(x: Int)(using desc: Description): X = X(desc.description)

    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].fromImplicit(1)
      make[Description].fromValue(Description("X"))
      make[X].fromImplicit(makeX)
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    context.get[Description]
    context.get[X]
  }

  "support bindings with function with type and implicit parameters" in {
    final case class Description[T](description: String)
    final case class X(s: String)

    def makeX[T](value: T)(implicit desc: Description[X]): X = X(desc.description)

    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].fromImplicit(1)
      make[Description[X]].fromValue(Description("X"))
      make[X].fromImplicit(makeX[Int])
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    context.get[Description[X]]
    context.get[X]
  }

  "support binding inside code block" in {
    final case class Description(description: String)
    final case class X(s: String)

    def makeX(x: Int)(using desc: Description): X = X(desc.description)

    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].fromImplicit(1)
      make[Description].fromValue(Description("X"))
      make[X].fromImplicit {
        (b: Int) => {
          val a = 1
          val desc = implicitly[Description]
          X(b.toString + desc.description)
        }
      }
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    context.get[Description]
    context.get[X]
  }

  "support binding with more than one implicit parameter" in {
    final case class Description(description: String)
    final case class X(s: String)

    def makeX(x: Int)(using desc: Description, moreDesc: String): X = X(desc.description + moreDesc)

    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].fromImplicit(1)
      make[String].fromImplicit("more-description")
      make[Description].fromImplicit(Description("X"))
      make[X].fromImplicit(makeX)
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    context.get[Description]
    context.get[X]
  }

  "support binding inside block with more than one implicit parameter" in {
    final case class Description(description: String)
    final case class X(s: String)

    def makeX(x: Int)(using desc: Description): X = X(desc.description)

    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].fromImplicit(1)
      make[String].fromImplicit("str")
      make[Description].fromValue(Description("X"))
      make[X].fromImplicit {
        (b: Int) => {
          val a = 1
          val desc = implicitly[Description].description + implicitly[String]
          X(desc + b.toString)
        }
      }
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    context.get[Description]
    context.get[X]
  }

  "support implicits with higher kinded types" in {
    trait Pointed[F[_]] {
      def point[A](a: A): F[A]
    }

    object Pointed {
      def apply[F[_] : Pointed]: Pointed[F] = implicitly

      implicit final val pointedList: Pointed[List] =
        new Pointed[List] {
          override def point[A](a: A): List[A] = List(a)
        }
    }

    case class Definition[F[_]: TagK: Pointed](getResult: Int) extends ModuleDef {
      addImplicit[Pointed[F]]
      make[Int].named("TestService").fromImplicit(getResult)
      make[F[String]].fromImplicit {
        (res: Int @Id("TestService")) => Pointed[F].point(s"Hello $res!")
      }
    }

    val injector = mkInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(Definition[List](1)))
    val context = injector.produce(plan).unsafeGet()

    context.get[List[String]] == List("Hello 1!")
  }

  "support 'by name' values" in {
    trait Pointed[F[_]] {
      def point[A](a: A): F[A]
    }

    object Pointed {
      def apply[F[_] : Pointed]: Pointed[F] = implicitly

      implicit final val pointedList: Pointed[List] =
        new Pointed[List] {
          override def point[A](a: A): List[A] = List(a)
        }
    }

    case class Definition[F[_] : TagK : Pointed](getResult: Int) extends ModuleDef {
      addImplicit[Pointed[F]]
      make[F[Any]].fromImplicit(Pointed[F].point(1: Any))
    }

    val injector = mkInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(Definition[List](1)))
    val context = injector.produce(plan).unsafeGet()

    context.get[List[Any]] == List(1)
  }
}
