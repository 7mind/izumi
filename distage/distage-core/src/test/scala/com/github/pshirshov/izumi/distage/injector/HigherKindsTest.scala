package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.HigherKindCases._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import distage._
import org.scalatest.WordSpec

import scala.language.higherKinds
import scala.reflect.runtime.universe.typeOf

class HigherKindsTest extends WordSpec with MkInjector {

  "support tagless final style module definitions" in {
    import HigherKindsCase1._

    case class Definition[F[_] : TagK : Pointed](getResult: Int) extends ModuleDef {
      // TODO: hmmm, what to do with this
      make[Pointed[F]].from(Pointed[F])

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
    val listPlan = listInjector.plan(Definition[List](5))
    val listContext = listInjector.produce(listPlan)

    assert(listContext.get[TestTrait].get == List(5))
    assert(listContext.get[TestServiceClass[List]].get == List(5))
    assert(listContext.get[TestServiceTrait[List]].get == List(10))
    assert(listContext.get[List[String]] == List("Hello 5!"))
    assert(listContext.get[List[Any]] == List(1))
    assert(listContext.get[Either[String, Boolean]] == Right(true))
    assert(listContext.get[Either[String, List[Int]]] == Right(List(1)))
    assert(listContext.get[List[Either[Int, List[String]]]] == List(Right(List("hello"))))

    val optionTInjector = mkInjector()
    val optionTPlan = optionTInjector.plan(Definition[OptionT[List, ?]](5))
    val optionTContext = optionTInjector.produce(optionTPlan)

    assert(optionTContext.get[TestTrait].get == OptionT(List(Option(5))))
    assert(optionTContext.get[TestServiceClass[OptionT[List, ?]]].get == OptionT(List(Option(5))))
    assert(optionTContext.get[TestServiceTrait[OptionT[List, ?]]].get == OptionT(List(Option(10))))
    assert(optionTContext.get[OptionT[List, String]] == OptionT(List(Option("Hello 5!"))))

    val eitherInjector = mkInjector()
    val eitherPlan = eitherInjector.plan(Definition[Either[String, ?]](5))
    val eitherContext = eitherInjector.produce(eitherPlan)

    assert(eitherContext.get[TestTrait].get == Right(5))
    assert(eitherContext.get[TestServiceClass[Either[String, ?]]].get == Right(5))
    assert(eitherContext.get[TestServiceTrait[Either[String, ?]]].get == Right(10))
    assert(eitherContext.get[Either[String, String]] == Right("Hello 5!"))
    assert(eitherContext.get[Either[Either[String, String], Either[String, Either[String, Either[String, Either[String, String]]]]]]
      == Right(Right(Right(Right(Right("aaa"))))))

    val idInjector = mkInjector()
    val idPlan = idInjector.plan(Definition[id](5))
    val idContext = idInjector.produce(idPlan)

    assert(idContext.get[TestTrait].get == 5)
    assert(idContext.get[TestServiceClass[id]].get == 5)
    assert(idContext.get[TestServiceTrait[id]].get == 10)
    assert(idContext.get[id[String]] == "Hello 5!")
  }

  "Compilation fail when trying produce Tag when in T[F[_], A] `T` is a generic" in {
    assertTypeError("""
      import HigherKindsCase1._

      abstract class Parent[T[_[_], _], F[_]: TagK, A: Tag] extends ModuleDef {
        make[T[F, A]]
      }
    """)
  }

  "TODO: Support [A, F[_]] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[C: Tag, R[_]: TagK: Pointed] extends ModuleDef {
      make[TestProvider[C, R]]
    }

    assert(new Parent[Int, List]{}.bindings.head.key.tpe == SafeType.get[TestProvider[Int, List]])
  }

  "TODO: Support [A, A, F[_]] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[A: Tag, C: Tag, R[_]: TagK: Pointed] extends ModuleDef {
      make[TestProvider0[A, C, R]]
    }

    assert(new Parent[Int, Boolean, List]{}.bindings.head.key.tpe == SafeType.get[TestProvider0[Int, Boolean, List]])
  }

  "TODO: support [A, F[_], G[_]] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[A: Tag, F[_]: TagK, R[_]: TagK: Pointed] extends ModuleDef {
      make[TestProvider1[A, F, R]]
    }

    assert(new Parent[Int, List, List]{}.bindings.head.key.tpe == SafeType.get[TestProvider1[Int, List, List]])
  }

  "TODO: support [F[_], G[_], A] type shape" in {
    import HigherKindsCase1._

    abstract class Parent[F[_]: TagK, R[_]: TagK: Pointed, A: Tag] extends ModuleDef {
      make[TestProvider2[F, R, A]]
    }

    assert(new Parent[List, List, Int]{}.bindings.head.key.tpe == SafeType.get[TestProvider2[List, List, Int]])
  }

  "TagKK works sometimes" in {
    import com.github.pshirshov.izumi.distage.fixtures.HigherKindCases.HigherKindsCase2._

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

    val context = Injector().produce(definition)
    assert(context != null)

    assert(definition.t0.tag.tpe =:= typeOf[TestCovariantTC[Either]])
    assert(definition.t1.tag.tpe =:= typeOf[TestClassFG[Either, Option]])
    assert(definition.t2.tag.tpe =:= typeOf[TestClassFA[Either, Int]])
    assert(context.get[Either[String, Int]] == value)
  }

}
