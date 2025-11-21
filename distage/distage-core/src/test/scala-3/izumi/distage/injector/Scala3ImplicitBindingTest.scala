package izumi.distage.injector

import distage.*
import izumi.distage.model.exceptions.runtime.ProvisioningException
import izumi.functional.quasi.QuasiApplicative
import izumi.fundamentals.platform.assertions.ScalatestGuards
import izumi.reflect.Tag
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class Scala3ImplicitBindingTest extends AnyWordSpec with MkInjector with ScalatestGuards {

  final case class Description(description: String)
  final case class X(s: String)

  def makeX(x: Int)(using desc: Description): X = X(s"${desc.description}$x")

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

  class StaticTestRole[F[_]]

  "Scala 3 implicit bindings" should {

    "support bindings with function implicit parameters" in {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("X"))
        make[X].from(bindImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("X1"))
    }

    "support bindings with function with type and implicit parameters" in {
      final case class Description[T](description: String)
      final case class X(s: String)

      def makeX[T](value: T)(implicit desc: Description[X]): X = X(desc.description + value)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description[X]].fromValue(Description("X"))
        make[X].from(bindImplicits(makeX[Int]))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("X1"))
    }

    "support binding inside code block" in {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("X"))
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                locally {
                  val a = 2
                  val desc = implicitly[Description]
                  X(b.toString + desc.description + a)
                }
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("1X2"))
    }

    "support binding with more than one implicit parameter" in {
      def makeX2(x: Int)(using desc: Description, moreDesc: String): X = X(desc.description + moreDesc + x)

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[String].fromValue("more-description")
        make[Description].fromValue(Description("X"))
        make[X].from(bindImplicits(makeX2))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("Xmore-description1"))
    }

    "support binding inside block with more than one implicit parameter" in {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[String].fromValue("str")
        make[Description].fromValue(Description("X"))
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 2
                val desc = implicitly[Description].description + implicitly[String]
                X(desc + b.toString + a)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("Xstr12"))
    }

    "support implicits with higher kinded types" in {
      case class Definition[F[_]: TagK](getResult: Int, p: Pointed[F]) extends ModuleDef {
        make[Pointed[F]].from(p)
        make[Int].named("TestService").fromValue(getResult)
        make[F[String]].from {
          bindImplicits {
            (res: Int @Id("TestService")) => Pointed[F].point(s"Hello $res!")
          }
        }
      }

      val injector = mkInjector()
      val plan = injector.planUnsafe(PlannerInput.everything(Definition[List](1, implicitly)))
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[List[String]] == List("Hello 1!"))
    }

    "support 'by name' values" in {
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
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 2
                implicit val description: Description = Description("desc")
                val desc = implicitly[Description]
                X(b.toString + desc.description + a)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("1desc2"))
    }

    "should not override given inside the block" in {
      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("from-di"))
        make[X].from {
          bindImplicits {
            (b: Int) =>
              {
                val a = 2
                given description: Description = Description("desc")
                val desc = implicitly[Description]
                X(b.toString + desc.description + a)
              }
          }
        }
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("1desc2"))
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

    "should discharge dummies if functoid macro did not do so" in {
      def makeX(using desc: Description, d: Short): Functoid[X] =
        Functoid((x: Int) => X(d.toString + x.toString + desc.description))

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
      def makeX(implicit desc: Description): X = X(desc.description)

      implicit val description: Description = Description("description")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Description].fromValue(Description("desc"))
        make[X].from(bindAllImplicits(makeX))
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Description].description == "desc")
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
      def makeX(x: Int)(implicit desc: Description): X = X(s"${desc.description}$x")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(1)
        make[Description].fromValue(Description("desc"))
        many[X].add(bindImplicits(makeX))
        many[X].named("set").addSet {
          bindImplicits {
            Functoid[Set[X]] { // inference breaks down here due to combination of `addSet` overload and picking up Functoid Function1 conversion instead of block conversion, because Set inherits Function1
              Set(X(s"${implicitly[Int]}-${implicitly[Description].description}-${implicitly[Set[X]]}"))
            }
          }
        }
        make[X]
          .named("x").from(bindImplicits {
            X(s"${implicitly[Int]}-${implicitly[Description].description}-${implicitly[Set[X]]}")
          })
        many[X]
          .named("set2").addSet(bindImplicits {
            (x: Int) =>
              Set(X(s"${x + x}-${implicitly[Description].description}-${implicitly[Set[X]]}"))
          })
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Set[X]]("set") == Set(X("1-desc-Set(X(desc1))")))
      assert(context.get[Set[X]]("set2") == Set(X("2-desc-Set(X(desc1))")))
      assert(context.get[X]("x") == X("1-desc-Set(X(desc1))"))
    }

    "fail to find implicit for non specific type (this is a Scala 3 inference limitation, not ours)" in {
      trait A[T]
      object A {
        implicit val intA: A[Int] = new A[Int] {}
      }

      final case class X[T](a: A[T])
      def makeX[T](implicit a: A[T]): X[Any] = X[Any](a.asInstanceOf[A[Any]])

      val definition = new ModuleDef {
        addImplicit[A[Int]]
        make[X[Any]].from(bindImplicits(makeX))
      }

      val injector = mkInjector()

      intercept[ProvisioningException] {
        injector.produceGet[X[Any]](definition).unsafeGet()
      }
    }

    "id annotation on explicit arguments still works" in {
      def makeX(x: Int @Id("2"))(implicit description: Description): X = X(s"${description.description}$x")
      def makeXN(@Id("2") x: Int)(implicit description: Description): X = X(s"${description.description}$x")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].named("2").fromValue(2)
        make[Description].fromValue(Description("desc"))
        make[X].from(bindImplicits(makeX))
        make[X].named("n").from(bindImplicits(makeXN))
        make[X]
          .named("x2").from(bindImplicits {
            (x: Int @Id("2")) => X(((x + x) * 2).toString + implicitly[Description].description)
          })
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("desc2"))
      assert(context.get[X]("n") == X("desc2"))
      assert(context.get[X]("x2") == X("8desc"))
    }

    "id annotation on implicit arguments works" in {
      def makeX(x: Int)(implicit description: Description @Id("p")): X = X(s"${description.description}$x")
      def makeXU(x: Int)(using Description @Id("p")): X = X(s"${summon[Description].description}$x")
      def makeXN(x: Int)(implicit @Id("p") description: Description): X = X(s"${description.description}$x")
      def makeXNU(x: Int)(using @Id("p") description: Description): X = X(s"${summon[Description].description}$x")

      val definition = PlannerInput.everything(new ModuleDef {
        make[Int].fromValue(2)
        make[Description].named("p").fromValue(Description("pest"))
        make[X].from(bindImplicits(makeX))
        make[X].named("using").from(bindImplicits(makeXU))
        make[X].named("n").from(bindImplicits(makeXN))
        make[X].named("nusing").from(bindImplicits(makeXNU))
        make[X]
          .named("block").from(bindImplicits {
            (x: Int) => X(((x + x) * 2).toString + implicitly[Description @Id("p")].description)
          })
      })

      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[X] == X("pest2"))
      assert(context.get[X]("using") == X("pest2"))
      assert(context.get[X]("n") == X("pest2"))
      assert(context.get[X]("nusing") == X("pest2"))
      assert(context.get[X]("block") == X("8pest"))
    }

    "progression test: id annotation support on implicit arguments names breaks if some of them are resolved" in {
      def makeX(implicit description: Description @Id("p"), x: Int): X = X(s"${description.description}$x")
      def makeXN(implicit @Id("p") description: Description, x: Int): X = X(s"${description.description}$x")

      implicit val xInt: Int = 2

      val definition = new ModuleDef {
        make[Description].named("p").fromValue(Description("pest"))
        make[X].from(bindImplicits(makeX))
        make[X].named("n").from(bindImplicits(makeXN))
      }

      val injector = mkInjector()

      injector.produceRun(definition) {
        (x: X) =>
          assert(x == X("pest2"))
      }

      intercept[ProvisioningException] {
        injector.produceRun(definition) {
          (x: X @Id("n")) =>
            assert(x == X("pest2"))
        }
      }
    }

    "progression test: Cannot support StaticTestRole[F] test case unless summonIgnoring is made transitive in the compiler" in {
      // What's happening here: izumi.reflect.TagMacro makes a nested implicit searches to assemble a
      // Tag.appliedTag(LightTypeTag...) expression. Because `summonIgnoring` is not transitive, nested searches find
      // dummy parameters instead of recursing into macro.
      // Because in Tag.appliedTag expression, every parameter is just `LightTypeTag`, it's impossible to recover the initial
      // sought implicit type, because it's no longer anywhere in the tree, so it's impossible to recover from this state
      // by discharging dummies. (In the usual case, e.g. `implicitly[Functor[T]](contextual$2: Dummy)` we recover the sought
      // implicit type by taking it from `implicitly[Functor[T]]` part of the tree, which has a type `Functor[T] => Functor[T]`.
      // But that's impossible in this case. In `Tag.appliedTag(contextual$2.tag)` expression, `Tag.appiedTag` has type
      // `LightTypeTag* => LightTypeTag`, `contextual$2.tag` has type `LightTypeTag`, `contextual$2` has type `Dummy`,
      // all while the initial sought implicit type was e.g. `Tag[StaticTestRole[F]]` – because the implicit search was
      // done inside the macro, not by filling implicit holes in the tree, the initial type is unrecoverable via our simple
      // dummy discharging strategy)
      val err = intercept[TestFailedException](assertCompiles("""
      def definition[F[_]: TagK, G[_]: TagK] = PlannerInput.everything(new ModuleDef {
        make[StaticTestRole[F]].fromEffect {
          bindImplicits {
            ClassConstructor[StaticTestRole[F]]
              .flatAp((G: QuasiApplicative[G]) => G.pure(_: StaticTestRole[F]))
          }
        }
      })
      """))
      assert(err.getMessage.contains("Couldn't discharge dummy of type"))
      assert(err.getMessage.contains(" & izumi.reflect.Tag[G["))
    }

  }

}
