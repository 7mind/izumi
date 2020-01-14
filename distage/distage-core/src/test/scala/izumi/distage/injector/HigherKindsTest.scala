package izumi.distage.injector

import distage._
import izumi.distage.fixtures.HigherKindCases._
import izumi.distage.model.PlannerInput
import izumi.fundamentals.reflection.macrortti.LTag
import org.scalatest.wordspec.AnyWordSpec

class HigherKindsTest extends AnyWordSpec with MkInjector {

  "support tagless final style module definitions" in {
    import HigherKindsCase1._

    case class Definition[F[_] : TagK : Pointed](getResult: Int) extends ModuleDef {
      // TODO: hmmm, what to do with this
      addImplicit[Pointed[F]]

      make[TestTrait].from[TestServiceClass[F]]
      make[TestServiceClass[F]]
      make[TestServiceTrait[F]]
      make[Int].named("TestService").from(getResult)
      make[F[String]].from { res: Int @Id("TestService") => Pointed[F].point(s"Hello $res!") }
      make[Either[String, Boolean]].from(Right(true))

      make[Either[F[String], F[F[F[F[String]]]]]].from(Right(Pointed[F].point(Pointed[F].point(Pointed[F].point(Pointed[F].point("aaa"))))))

      make[F[Nothing]].from(null.asInstanceOf[F[Nothing]])
      make[F[Any]].from(Pointed[F].point(1: Any))

      make[Either[String, F[Int]]].from { fAnyInt: F[Any] => Right[String, F[Int]](fAnyInt.asInstanceOf[F[Int]]) }
      make[F[Either[Int, F[String]]]].from(Pointed[F].point(Right[Int, F[String]](Pointed[F].point("hello")): Either[Int, F[String]]))
    }

    val listInjector = mkInjector()
    val listPlan = listInjector.plan(PlannerInput.noGc(Definition[List](5)))
    val listContext = listInjector.produceUnsafe(listPlan)

    assert(listContext.get[TestTrait].get == List(5))
    assert(listContext.get[TestServiceClass[List]].get == List(5))
    assert(listContext.get[TestServiceTrait[List]].get == List(10))
    assert(listContext.get[List[String]] == List("Hello 5!"))
    assert(listContext.get[List[Any]] == List(1))
    assert(listContext.get[Either[String, Boolean]] == Right(true))
    assert(listContext.get[Either[String, List[Int]]] == Right(List(1)))
    assert(listContext.get[List[Either[Int, List[String]]]] == List(Right(List("hello"))))

    val optionTInjector = mkInjector()
    val optionTPlan = optionTInjector.plan(PlannerInput.noGc(Definition[OptionT[List, ?]](5)))
    val optionTContext = optionTInjector.produceUnsafe(optionTPlan)

    assert(optionTContext.get[TestTrait].get == OptionT(List(Option(5))))
    assert(optionTContext.get[TestServiceClass[OptionT[List, ?]]].get == OptionT(List(Option(5))))
    assert(optionTContext.get[TestServiceTrait[OptionT[List, ?]]].get == OptionT(List(Option(10))))
    assert(optionTContext.get[OptionT[List, String]] == OptionT(List(Option("Hello 5!"))))

    val eitherInjector = mkInjector()
    val eitherPlan = eitherInjector.plan(PlannerInput.noGc(Definition[Either[String, ?]](5)))

    val eitherContext = eitherInjector.produceUnsafe(eitherPlan)

    assert(eitherContext.get[TestTrait].get == Right(5))
    assert(eitherContext.get[TestServiceClass[Either[String, ?]]].get == Right(5))
    assert(eitherContext.get[TestServiceTrait[Either[String, ?]]].get == Right(10))
    assert(eitherContext.get[Either[String, String]] == Right("Hello 5!"))
    assert(eitherContext.get[Either[Either[String, String], Either[String, Either[String, Either[String, Either[String, String]]]]]]
      == Right(Right(Right(Right(Right("aaa"))))))

    val idInjector = mkInjector()
    val idPlan = idInjector.plan(PlannerInput.noGc(Definition[id](5)))
    val idContext = idInjector.produceUnsafe(idPlan)

    assert(idContext.get[TestTrait].get == 5)
    assert(idContext.get[TestServiceClass[id]].get == 5)
    assert(idContext.get[TestServiceTrait[id]].get == 10)
    assert(idContext.get[id[String]] == "Hello 5!")
  }

  "Compilation should fail when trying to materialize a Tag when T has no Tag in T[F[_], A]" in {
    assertTypeError("""
      import HigherKindsCase1._

      abstract class Parent[T[_[_], _], F[_]: TagK, A: Tag] extends ModuleDef {
        make[T[F, A]]
      }
    """)
  }

  "Support [A, F[_]] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[C: Tag, R[_]: TagK: Pointed] extends ModuleDef {
      make[TestProvider[C, R]]
    }

    assert(new Parent[Int, List]{}.bindings.head.key.tpe == SafeType.get[TestProvider[Int, List]])
  }

  "Support [A, A, F[_]] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[A: Tag, C: Tag, R[_]: TagK: Pointed] extends ModuleDef {
      make[TestProvider0[A, C, R]]
    }

    assert(new Parent[Int, Boolean, List]{}.bindings.head.key.tpe == SafeType.get[TestProvider0[Int, Boolean, List]])
  }

  "support [A, F[_], G[_]] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[A: Tag, F[_]: TagK, R[_]: TagK: Pointed] extends ModuleDef {
      make[TestProvider1[A, F, R]]
    }

    assert(new Parent[Int, List, List]{}.bindings.head.key.tpe == SafeType.get[TestProvider1[Int, List, List]])
  }

  "support [F[_], G[_], A] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[F[_]: TagK, R[_]: TagK: Pointed, A: Tag] extends ModuleDef {
      make[TestProvider2[F, R, A]]
    }

    assert(new Parent[List, List, Int]{}.bindings.head.key.tpe == SafeType.get[TestProvider2[List, List, Int]])
  }

  "TagKK works" in {
    import izumi.distage.fixtures.HigherKindCases.HigherKindsCase2._

    class Definition[F[+_, +_]: TagKK: TestCovariantTC, G[_]: TagK, A: Tag](v: F[String, Int]) extends ModuleDef {
      make[TestCovariantTC[F]]
      make[TestCovariantTC[Either]]
      final val t0 = Tag[TestCovariantTC[F]]

      make[TestClassFG[F, G]]
      make[TestClassFG[Either, G]]
      make[TestClassFG[F, Option]]
      make[TestClassFG[Either, Option]]
      final val t1 = Tag[TestClassFG[F, G]]

      make[TestClassFA[F, A]]
      make[TestClassFA[Either, A]]
      make[TestClassFA[F, Int]]
      make[TestClassFA[Either, Int]]
      final val t2 = Tag[TestClassFA[F, A]]

      make[F[String, Int]].from(v)
    }

    val value: Either[String, Int] = Right(5)
    val definition = new Definition[Either, Option, Int](value)

    val context = Injector.Standard().produceUnsafe(PlannerInput.noGc(definition))
    assert(context != null)

    assert(definition.t0.tag =:= LTag[TestCovariantTC[Either]].tag)
    assert(definition.t1.tag =:= LTag[TestClassFG[Either, Option]].tag)
    assert(definition.t2.tag =:= LTag[TestClassFA[Either, Int]].tag)
    assert(context.get[Either[String, Int]] == value)
  }

}
