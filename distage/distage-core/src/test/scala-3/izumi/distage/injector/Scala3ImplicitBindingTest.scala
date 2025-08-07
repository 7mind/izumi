package izumi.distage.injector

import distage.*
import izumi.functional.quasi.QuasiApplicative
import izumi.fundamentals.platform.assertions.ScalatestGuards
import izumi.reflect.Tag
import org.scalatest.wordspec.AnyWordSpec

class Scala3ImplicitBindingTest extends AnyWordSpec with MkInjector with ScalatestGuards {

  "Scala 3 implicit bindings" should {

    "support bindings with function implicit parameters" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(using desc: Description): X = X(desc.description)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("X"))
        make[X].from(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("X"))
      assert(context.get[X] == X("X"))
    }

    "support bindings with function with type and implicit parameters" in {
      final case class Description[T](description: String)
      final case class X(s: String)

      def makeX[T](value: T)(implicit desc: Description[X]): X = X(desc.description)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description[X]].fromValue(Description("X"))
        make[X].from(bindImplicits(makeX[Int]))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description[X]] == Description("X"))
      assert(context.get[X] == X("X"))
    }

    "support binding inside code block" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(using desc: Description): X = X(desc.description)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("X"))
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 1
                val desc = implicitly[Description]
                X(b.toString + desc.description)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("X"))
      assert(context.get[X] == X("1X"))
    }

    "support binding with more than one implicit parameter" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(using desc: Description, moreDesc: String): X = X(desc.description + moreDesc)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[String].fromValue("more-description")
        make[Description].fromValue(Description("X"))
        make[X].from(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("X"))
      assert(context.get[X] == X("Xmore-description"))
    }

    "support binding inside block with more than one implicit parameter" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(using desc: Description): X = X(desc.description)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[String].fromValue("str")
        make[Description].fromValue(Description("X"))
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 1
                val desc = implicitly[Description].description + implicitly[String]
                X(desc + b.toString)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("X"))
      assert(context.get[X] == X("Xstr1"))
    }

    "support implicits with higher kinded types" in {
      trait Pointed[F[_]] {
        def point[A](a: A): F[A]
      }

      object Pointed {
        def apply[F[_]: Pointed]: Pointed[F] = implicitly

        implicit final val pointedList: Pointed[List] =
          new Pointed[List] {
            override def point[A](a: A): List[A] = List(a)
          }
      }

      case class Definition[F[_]: TagK: Pointed](getResult: Int) extends ModuleDef {
        addImplicit[Pointed[F]]
        make[Int].named("TestService").fromValue(getResult)
        make[F[String]].from {
          bindImplicits {
            (res: Int @Id("TestService")) => Pointed[F].point(s"Hello $res!")
          }
        }
      }

      val injector = mkInjector()
      val plan = injector.planUnsafe(PlannerInput.everything(Definition[List](1)))
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[List[String]] == List("Hello 1!"))
    }

    "support 'by name' values" in {
      trait Pointed[F[_]] {
        def point[A](a: A): F[A]
      }

      object Pointed {
        def apply[F[_]: Pointed]: Pointed[F] = implicitly

        implicit final val pointedList: Pointed[List] =
          new Pointed[List] {
            override def point[A](a: A): List[A] = List(a)
          }
      }

      class Definition[F[+_]: TagK](getResult: Int, p: Pointed[F]) extends ModuleDef {
        make[Pointed[F]].from(p)
        make[F[Any]].from(bindImplicits(Pointed[F].point(1)))
      }

      val injector = mkInjector()
      val plan = injector.planUnsafe(PlannerInput.everything(Definition[List](1, implicitly)))
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[List[Any]] == List(1))
    }

    "should not override implicit inside the block" in {
      final case class Description(description: String)
      final case class X(s: String)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 1
                implicit val description: Description = Description("desc")
                val desc = implicitly[Description]
                X(b.toString + desc.description)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("1desc"))
    }

    "should not override given inside the block" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(using desc: Description): X = X(desc.description)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("from-di"))
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 1
                given description: Description = Description("desc")
                val desc = implicitly[Description]
                X(b.toString + desc.description)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("1desc"))
    }

    "should ignore dummy implicit during implicit search if there is implicit defined outside of the object graph" in {
      final case class Description[T](description: String)
      final case class X(s: String, i: Any, t1: Tag[?], t2: Tag[?])

      def makeX[T: Tag](value: T)(implicit desc: Description[X], t: Tag[X]): X = X(desc.description, value, Tag[T], Tag[X])

      implicit val description: Description[X] = Description[X]("description")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[X].from(bindImplicits(makeX[Int]))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      val x = context.get[X]
      assert(x.s == description.description)
      assert(x.i == 1)
      assert(x.t1 == Tag[Int])
      assert(x.t2 == Tag[X])
    }

    "should summon implicits if functoid passed to a function" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(using desc: Description, d: Short): Functoid[X] = Functoid((x: Int) => X(d.toString + x.toString + desc.description))

      implicit val desc: Description = Description("desc")
      implicit val double: Short = 2

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[X].from(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("21desc"))
    }

    "ignore implicits defined and only use objects from the object graph" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(value: Int)(implicit desc: Description): X = X(desc.description)

      implicit val description: Description = Description("description")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("desc"))
        make[X].from(bindDIImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      val desc = context.get[Description]
      assert(desc.description == "desc")
      assert(context.get[X] == X("desc"))
    }

    "should ignore Tag* dummies using summonIgnoring, even if they are used in context function body inside Functoid macro" in {
      var functoid: Functoid[Any] = null

      def definition[F[_]: TagK] = PlannerInput.everything(new ModuleDef {
        make[Int].fromEffect {
          bindImplicits {
            val x = Functoid[F[Int]] {
              (F: QuasiApplicative[F]) =>
                // ok case
                Predef.require(implicitly[Tag[QuasiApplicative[F]]] ne null)
                Predef.require(implicitly[Tag[F[Int]]] ne null)

                F.pure[Int](1)
            }
            functoid = x
            x
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition[Identity])
      val context = injector.produce(plan).unsafeGet()

      assert(functoid.get.diKeys.map(_.tpe.tag) == List(Tag[QuasiApplicative[Identity]].tag))
      assert(functoid.get.ret == SafeType.get[Int])
      assert(context.get[Int] == 1)
    }

    "should ignore Tag* dummies using summonIgnoring, even if they are used in context function body outside of Functoid macro" in {
      var functoid: Functoid[Any] = null

      def definition[F[_]: TagK] = PlannerInput.everything(new ModuleDef {
        make[Int].fromEffect {
          bindImplicits {
            // bad case
            Predef.require(implicitly[Tag[QuasiApplicative[F]]] ne null)
            Predef.require(implicitly[Tag[F[Int]]] ne null)

            val x = Functoid.apply[F[Int]] {
              (F: QuasiApplicative[F]) => F.pure[Int](1)
            }
            functoid = x
            x
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition[Identity])
      val context = injector.produce(plan).unsafeGet()

      assert(functoid.get.diKeys.map(_.tpe.tag) == List(Tag[QuasiApplicative[Identity]].tag))
      assert(functoid.get.ret == SafeType.get[Int])
      assert(context.get[Int] == 1)
    }

    "support implicits in effects" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX[F[_]: QuasiApplicative](value: Int)(implicit desc: Description): F[X] =
        QuasiApplicative.apply[F].pure(X(desc.description + value.toString))

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("desc"))
        make[X].fromEffect[Identity, X](bindImplicits(makeX[Identity]))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("desc"))
      assert(context.get[X] == X("desc1"))
    }

    "support implicits in resource class" in {
      final case class Description(description: String)
      final case class X(s: String)

      class XResource(implicit desc: Description) extends Lifecycle.Simple[X] {
        override def acquire: X = X(desc.description)
        override def release(resource: X): Unit = ()
      }

      val definition = PlannerInput.everything(new ModuleDef {
        make[Description].fromValue(Description("desc"))
        make[X].fromResource[XResource]
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("desc"))
      assert(context.get[X] == X("desc"))
    }

    "support implicits in resource" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(implicit desc: Description): Lifecycle[Identity, X] =
        Lifecycle.make(X(desc.description): Identity[X])(_ => ())

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("desc"))
        make[X].fromResource(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description] == Description("desc"))
      assert(context.get[X] == X("desc"))
    }

    "support implicits in sets" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(implicit desc: Description): X = X(s"${desc.description}$x")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("desc"))
        many[X].add(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Set[X]] == Set(X("desc1")))
    }

    "support implicits in addSet" in {
      final case class Description(description: String)
      final case class X(s: String)

      def makeX(x: Int)(implicit desc: Description): X = X(s"${desc.description}$x")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("desc"))
        many[X].add(bindImplicits(makeX))
        many[X].named("set").addSet[Set[X]] {
          bindImplicits {
            Set(X(s"${implicitly[Int]}-${implicitly[Description].description}-${implicitly[Set[X]]}"))
          }: Functoid[Set[X]] // Weird, inference breaks here due to `addSet` overload, but not in `from` which also has an overload
        }
        make[X].named("x").from {
          bindImplicits {
            X(s"${implicitly[Int]}-${implicitly[Description].description}-${implicitly[Set[X]]}")
          }
        }
        many[X].named("set2").addSet[Set[X]] {
          bindImplicits {
            (x: Int) => Set(X(s"${x + x}-${implicitly[Description].description}-${implicitly[Set[X]]}"))
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Set[X]]("set") == Set(X("1-desc-Set(X(desc1))")))
      assert(context.get[Set[X]]("set2") == Set(X("2-desc-Set(X(desc1))")))
      assert(context.get[X]("x") == X("1-desc-Set(X(desc1))"))
    }

    "fail to find implicit for non specific type" in {
      trait A[T]
      object A {
        implicit val intA: A[Int] = new A[Int] {}
      }

      final case class X[T](a: A[T])
      def makeX[T](x: Int)(implicit a: A[T]): X[Any] = X[Any](a.asInstanceOf[A[Any]])

      val definition = PlannerInput.everything(new ModuleDef {
        addImplicit[A[Int]]
        make[X[Any]].from(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      broken {
        assert(context.get[X[Any]] == X(A.intA))
      }
    }

  }

}
