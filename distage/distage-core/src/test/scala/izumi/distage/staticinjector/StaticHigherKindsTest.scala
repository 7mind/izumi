package izumi.distage.staticinjector

import distage.{Id, TagK}
import izumi.distage.fixtures.HigherKindCases.HigherKindsCase1
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import org.scalatest.wordspec.AnyWordSpec

class StaticHigherKindsTest extends AnyWordSpec with MkInjector {

  "macros support tagless final style module definitions" in {
    import HigherKindsCase1._

    case class Definition[F[_]: TagK: Pointed](getResult: Int) extends ModuleDef {
      // TODO: hmmm, what to do with this
      make[Pointed[F]].from(Pointed[F])

      make[TestTrait].from[TestServiceClass[F]]
      make[TestServiceClass[F]]
      make[TestServiceTrait[F]]
      make[Int].named("TestService").from(getResult)
      make[F[String]].from { res: Int @Id("TestService") => Pointed[F].point(s"Hello $res!") }
      make[Either[String, Boolean]].from(Right(true))

      //        TODO: Nothing doesn't resolve properly yet when F is unknown...
      //        make[F[Nothing]]
      //        make[Either[String, F[Int]]].from(Right(Pointed[F].point(1)))
      make[F[Any]].from(Pointed[F].point(1: Any))
      make[Either[String, F[Int]]].from { fAnyInt: F[Any] => Right[String, F[Int]](fAnyInt.asInstanceOf[F[Int]]) }
      make[F[Either[Int, F[String]]]].from(Pointed[F].point(Right[Int, F[String]](Pointed[F].point("hello")): Either[Int, F[String]]))
    }

    val listInjector = mkNoCyclesInjector()
    val listPlan = listInjector.plan(PlannerInput.noGc(Definition[List](5)))
    val listContext = listInjector.produce(listPlan).unsafeGet()

    assert(listContext.get[TestTrait].get == List(5))
    assert(listContext.get[TestServiceClass[List]].get == List(5))
    assert(listContext.get[TestServiceTrait[List]].get == List(10))
    assert(listContext.get[List[String]] == List("Hello 5!"))
    assert(listContext.get[List[Any]] == List(1))
    assert(listContext.get[Either[String, Boolean]] == Right(true))
    assert(listContext.get[Either[String, List[Int]]] == Right(List(1)))
    assert(listContext.get[List[Either[Int, List[String]]]] == List(Right(List("hello"))))

    val optionTInjector = mkNoCyclesInjector()
    val optionTPlan = optionTInjector.plan(PlannerInput.noGc(Definition[OptionT[List, ?]](5)))
    val optionTContext = optionTInjector.produce(optionTPlan).unsafeGet()

    assert(optionTContext.get[TestTrait].get == OptionT(List(Option(5))))
    assert(optionTContext.get[TestServiceClass[OptionT[List, ?]]].get == OptionT(List(Option(5))))
    assert(optionTContext.get[TestServiceTrait[OptionT[List, ?]]].get == OptionT(List(Option(10))))
    assert(optionTContext.get[OptionT[List, String]] == OptionT(List(Option("Hello 5!"))))

    val idInjector = mkNoCyclesInjector()
    val idPlan = idInjector.plan(PlannerInput.noGc(Definition[id](5)))
    val idContext = idInjector.produce(idPlan).unsafeGet()

    assert(idContext.get[TestTrait].get == 5)
    assert(idContext.get[TestServiceClass[id]].get == 5)
    assert(idContext.get[TestServiceTrait[id]].get == 10)
    assert(idContext.get[id[String]] == "Hello 5!")
  }


}
