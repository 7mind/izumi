package com.github.pshirshov.izumi.distage.staticinjector

import com.github.pshirshov.izumi.distage.Fixtures.{ProviderCase1, HigherKindsCase1}
import com.github.pshirshov.izumi.distage.model.definition.StaticDSL._
import com.github.pshirshov.izumi.distage.model.definition.StaticModuleDef
import distage.{Id, TagK}
import org.scalatest.WordSpec

import scala.language.higherKinds
import scala.util.Try

class StaticAdvancedTypesTest extends WordSpec with MkInjector {

  "macros can handle class local path-dependent injections" in {
    val definition = new StaticModuleDef {
      stat[TopLevelPathDepTest.TestClass]
      stat[TopLevelPathDepTest.TestDependency]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)

    assert(context.get[TopLevelPathDepTest.TestClass].a != null)
  }

  "macros can handle inner path-dependent injections" in {
    new InnerPathDepTest().testCase
  }

  "progression test: macros can't handle function local path-dependent injections" in {
    val fail = Try {
      import ProviderCase1._

      val testProviderModule = new TestProviderModule

      val definition = new StaticModuleDef {
        stat[testProviderModule.TestClass]
        stat[testProviderModule.TestDependency]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[testProviderModule.TestClass].a != null)
    }.isFailure
    assert(fail)
  }

  "macros support tagless final style module definitions" in {
    import HigherKindsCase1._

    case class Definition[F[_] : TagK : Pointed](getResult: Int) extends StaticModuleDef {
      // FIXME: hmmm, what to do with this
      make[Pointed[F]].from(Pointed[F])

      make[TestTrait].stat[TestServiceClass[F]]
      stat[TestServiceClass[F]]
      stat[TestServiceTrait[F]]
      make[Int].named("TestService").from(getResult)
      make[F[String]].from { res: Int @Id("TestService") => Pointed[F].point(s"Hello $res!") }
      make[Either[String, Boolean]].from(Right(true))

      //        FIXME: Nothing doesn't resolve properly yet when F is unknown...
      //        make[F[Nothing]]
      //        make[Either[String, F[Int]]].from(Right(Pointed[F].point(1)))
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

    val idInjector = mkInjector()
    val idPlan = idInjector.plan(Definition[id](5))
    val idContext = idInjector.produce(idPlan)

    assert(idContext.get[TestTrait].get == 5)
    assert(idContext.get[TestServiceClass[id]].get == 5)
    assert(idContext.get[TestServiceTrait[id]].get == 10)
    assert(idContext.get[id[String]] == "Hello 5!")
  }

  class InnerPathDepTest extends ProviderCase1.TestProviderModule {
    private val definition = new StaticModuleDef {
      stat[TestClass]
      stat[TestDependency]
    }

    def testCase = {
      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends ProviderCase1.TestProviderModule

}
