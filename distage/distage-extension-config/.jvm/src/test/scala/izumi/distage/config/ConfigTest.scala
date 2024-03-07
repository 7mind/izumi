package izumi.distage.config

import com.github.pshirshov.configapp.SealedTrait.CaseClass2
import com.github.pshirshov.configapp.SealedTrait2.{No, Yes}
import com.github.pshirshov.configapp._
import com.typesafe.config._
import distage.Injector
import izumi.distage.config.model.AppConfig
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListSet

final class ConfigTest extends AnyWordSpec {
  def mkConfigModule(path: String)(p: PlannerInput): PlannerInput = {
    p.copy(bindings =
      p.bindings ++
      mkModule(ConfigFactory.load(path, ConfigParseOptions.defaults().setAllowMissing(false), ConfigResolveOptions.noSystem()))
    )
  }

  def mkModule(config: Config): AppConfigModule = {
    val appConfig = AppConfig(config, List.empty, List.empty)
    new AppConfigModule(appConfig)
  }

  "Config resolver" should {

//    "be idempotent under Injector.finish" in {
//      val injector = Injector(new ConfigPathExtractorModule)
//      val plan = injector.plan(mkConfigModule("distage-config-test.conf")(TestConfigApp.definition))
//
//      val plan2 = injector.finish(plan.toSemi)
//      val context = injector.produce(plan2).unsafeGet()
//
//      assert(context.get[HttpServer1].listenOn.port == 8081)
//      assert(context.get[HttpServer2].listenOn.port == 8082)
//
//      assert(context.get[DataPuller1].target.port == 9001)
//      assert(context.get[DataPuller2].target.port == 9002)
//      assert(context.get[DataPuller3].target.port == 9003)
//
//      assert(context.get[Set[TestAppService]].size == 5)
//    }

    "resolve config maps" in {
      val injector = Injector()
      val plan = injector.planUnsafe(mkConfigModule("map-test.conf")(TestConfigReaders.mapDefinition))

      val context = injector.produce(plan).unsafeGet()

      // FIXME: pureconfig can't read specialized map types
//      assert(context.get[Service[MapCaseClass]].conf.mymap.isInstanceOf[mutable.LinkedHashMap[?, ?]])
      assert(context.get[Service[MapCaseClass]].conf.mymap.isInstanceOf[Map[?, ?]])
      assert(context.get[Service[MapCaseClass]].conf.mymap.keySet == Set("service1", "service2", "service3", "service4", "service5", "service6"))
      assert(context.get[Service[MapCaseClass]].conf.mymap.values.forall(_.host == "localhost"))
    }

    "The order is not preserved in config maps due to limitations of typesafe-config" in {
      val context = Injector().produce(mkConfigModule("map-test.conf")(TestConfigReaders.mapDefinition)).unsafeGet()

      assert(context.get[Service[MapCaseClass]].conf.mymap.toList.map(_._1) != List("service1", "service2", "service3", "service4", "service5", "service6"))
      assert(context.get[Service[MapCaseClass]].conf.mymap.keySet == Set("service1", "service2", "service3", "service4", "service5", "service6"))
    }

    "resolve config lists" in {
      val injector = Injector()
      val plan = injector.planUnsafe(mkConfigModule("list-test.conf")(TestConfigReaders.listDefinition))

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Service[ListCaseClass]].conf.mylist.isInstanceOf[IndexedSeq[?]])
      assert(context.get[Service[ListCaseClass]].conf.mylist.head.isInstanceOf[ListSet[?]])
      assert(
        context.get[Service[ListCaseClass]].conf.mylist.head ==
        Set(
          Wrapper(HostPort(80, "localhost")),
          Wrapper(HostPort(8080, "localhost")),
          Wrapper(HostPort(8888, "localhost")),
        )
      )
    }

    "resolve config options" in {
      val injector = Injector()
      val plan = injector.planUnsafe(mkConfigModule("opt-test.conf")(TestConfigReaders.optDefinition))

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Service[OptionCaseClass]].conf == OptionCaseClass(optInt = None, optCustomObject = None))
    }

    "resolve config tuples" in {
      val context = Injector()
        .produce(mkConfigModule("tuple-test.conf")(TestConfigReaders.tupleDefinition)).unsafeGet()

      assert(context.get[Service[TupleCaseClass]].conf == TupleCaseClass(tuple = (1, "two", false, Some(Right(List("r"))))))
    }

    "resolve using custom codecs" in {
      val context = Injector()
        .produce(mkConfigModule("custom-codec-test.conf")(TestConfigReaders.customCodecDefinition)).unsafeGet()

      assert(
        context.get[Service[CustomCaseClass]].conf == CustomCaseClass(
          CustomCodecObject(453),
          Map("a" -> CustomCodecObject(453), "b" -> CustomCodecObject(45)),
          Map("x" -> List(CustomCodecObject(45), CustomCodecObject(453), CustomCodecObject(1))),
        )
      )
    }

    "resolve config options (missing field)" in {
      val injector = Injector()
      val plan = injector.planUnsafe(mkConfigModule("opt-test-missing.conf")(TestConfigReaders.optDefinition))

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Service[OptionCaseClass]].conf == OptionCaseClass(optInt = None, optCustomObject = None))
    }

    "resolve backticks" in {
      val context = Injector()
        .produce(mkConfigModule("backticks-test.conf")(TestConfigReaders.backticksDefinition)).unsafeGet()

      assert(context.get[Service[BackticksCaseClass]].conf == BackticksCaseClass(true))
    }

    "resolve case classes with private fields" in {
      val context = Injector()
        .produce(mkConfigModule("private-fields-test.conf")(TestConfigReaders.privateFieldsCodecDefinition)).unsafeGet()

      assert(context.get[Service[PrivateCaseClass]].conf == PrivateCaseClass("super secret value"))
    }

    "resolve case classes with partially private fields" in {
      val context = Injector()
        .produce(mkConfigModule("partially-private-fields-test.conf")(TestConfigReaders.partiallyPrivateFieldsCodecDefinition)).unsafeGet()

      assert(context.get[Service[PartiallyPrivateCaseClass]].conf == PartiallyPrivateCaseClass("super secret value", true))
    }

    // Derivation for AnyVals is not supported on Scala 3, requires a custom macro / library like
    // https://github.com/softwaremill/tapir/blob/84e4b3cb3bb209ac6c4fde3aecb13ca71afe6609/core/src/main/scala-3/sttp/tapir/internal/CodecValueClassMacro.scala
//    "resolve case classes with AnyVal fields" in {
//      val context = Injector()
//        .produce(mkConfigModule("anyval-test.conf")(TestConfigReaders.anyvalCodecDefinition)).unsafeGet()
//
//      assert(context.get[Service[AnyValClass]].conf == AnyValClass(new AnyValInt(5)))
//    }

    "resolve config sealed traits (with progression test for https://github.com/scala/bug/issues/11645)" in {
      // FIXME: pureconfig-magnolia can't read enumerations properly
      val context1 =
        Injector()
          .produce(mkConfigModule("sealed-test1.conf")(TestConfigReaders.sealedDefinition)).unsafeGet()
      assert(context1.get[Service[SealedCaseClass]].conf == SealedCaseClass(SealedTrait.CaseClass1(1, "1", true, Yes)))

      val context2 =
        Injector()
          .produce(mkConfigModule("sealed-test2.conf")(TestConfigReaders.sealedDefinition)).unsafeGet()
      assert(context2.get[Service[SealedCaseClass]].conf.sealedTrait1.asInstanceOf[CaseClass2].sealedTrait2 eq No)
      assert(context2.get[Service[SealedCaseClass]].conf == SealedCaseClass(SealedTrait.CaseClass2(2, false, No)))
    }

  }

}
