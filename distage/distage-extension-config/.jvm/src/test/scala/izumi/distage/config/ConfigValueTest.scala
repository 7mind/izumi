//package izumi.distage.config
//
//import com.github.pshirshov.configapp.TestConfigReaders
//import izumi.distage.config.codec.ConfigMetaType
//import izumi.distage.config.model.ConfTag
//import izumi.distage.model.PlannerInput
//import org.scalatest.wordspec.AnyWordSpec
//
//final class ConfigValueTest extends AnyWordSpec {
//
//  def getConfTag(plannerInput: PlannerInput): ConfTag = {
//    val tags = plannerInput.bindings.iterator.flatMap {
//      b => b.tags.collect { case c: ConfTag => c }
//    }.toSeq
//    assert(tags.size == 1)
//    tags.head
//  }
//
//  "Config fields meta" should {
//
//    "be unknown for config maps" in {
//      val c = getConfTag(TestConfigReaders.mapDefinition)
//      assert(c.tpe == ConfigMetaType.TCaseClass(c.tpe.id, Seq("mymap" -> ConfigMetaType.TUnknown()
//    }
//
//    "be unknown for config lists" in {
//      val c = getConfTag(TestConfigReaders.listDefinition)
//      assert(c.tpe == ConfigMetaType.TCaseClass(c.tpe.id, Seq("mylist" -> ConfigMetaType.TUnknown()
//    }
//
//    "be as expected for config options" in {
//      val c = getConfTag(TestConfigReaders.optDefinition)
//      assert(
//        c.tpe == ConfigMetaType.TCaseClass(
//          c.tpe.id,
//          Seq(
//            "optInt" -> ConfigMetaType.TUnknown(),
//            "optCustomObject" -> ConfigMetaType.TCaseClass(c.tpe.id, Seq("value" -> ConfigMetaType.TUnknown(),
//          ),
//        )
//      )
//    }
//
//    "be unknown for config tuples" in {
//      val c = getConfTag(TestConfigReaders.tupleDefinition)
//      assert(c.tpe == ConfigMetaType.TCaseClass(Seq("tuple" -> ConfigMetaType.TUnknown()
//    }
//
//    "be unknown for custom codecs" in {
//      val c = getConfTag(TestConfigReaders.customCodecDefinition)
//      assert(
//        c.tpe == ConfigMetaType.TCaseClass(
//          Seq(
//            "customObject" -> ConfigMetaType.TUnknown(),
//            "mapCustomObject" -> ConfigMetaType.TUnknown(),
//            "mapListCustomObject" -> ConfigMetaType.TUnknown(),
//          )
//        )
//      )
//    }
//
//    "be as expected for backticks" in {
//      val c = getConfTag(TestConfigReaders.backticksDefinition)
//
//      assert(c.tpe == ConfigMetaType.TCaseClass(c.tpe.id, Seq("boo-lean" -> ConfigMetaType.TUnknown()
//    }
//
//    "be as expected for case classes with private fields" in {
//      val c = getConfTag(TestConfigReaders.privateFieldsCodecDefinition)
//
//      assert(c.tpe == ConfigMetaType.TCaseClass(c.tpe.id, Seq("private-custom-field-name" -> ConfigMetaType.TUnknown()
//    }
//
//    "be as expected for case classes with partially private fields" in {
//      val c = getConfTag(TestConfigReaders.partiallyPrivateFieldsCodecDefinition)
//
//      assert(
//        c.tpe == ConfigMetaType.TCaseClass(
//          Seq(
//            "private-custom-field-name" -> ConfigMetaType.TUnknown(),
//            "publicField" -> ConfigMetaType.TUnknown(),
//          )
//        )
//      )
//    }
//
//    "be as exptected for sealed traits" in {
//      val c = getConfTag(TestConfigReaders.sealedDefinition)
//
//      assert(
//        c.tpe ==
//        ConfigMetaType.TCaseClass(
//          Seq(
//            "sealedTrait1" ->
//            ConfigMetaType.TSealedTrait(
//              Set(
//                "CaseClass1" -> ConfigMetaType.TCaseClass(
//                  Seq(
//                    "int" -> ConfigMetaType.TUnknown(),
//                    "string" -> ConfigMetaType.TUnknown(),
//                    "boolean" -> ConfigMetaType.TUnknown(),
//                    "sealedTrait2" -> ConfigMetaType.TSealedTrait(
//
//                      Set(
//                        "Yes" -> ConfigMetaType.TCaseClass(c.tpe.id, Seq()),
//                        "No" -> ConfigMetaType.TCaseClass(Seq()),
//                      )
//                    ),
//                  )
//                ),
//                "CaseClass2" -> ConfigMetaType.TCaseClass(
//                  Seq(
//                    "int" -> ConfigMetaType.TUnknown(),
//                    "boolean" -> ConfigMetaType.TUnknown(),
//                    "sealedTrait2" -> ConfigMetaType.TSealedTrait(
//                      Set(
//                        "Yes" -> ConfigMetaType.TCaseClass(Seq()),
//                        "No" -> ConfigMetaType.TCaseClass(Seq()),
//                      )
//                    ),
//                  )
//                ),
//              )
//            )
//          )
//        )
//      )
//    }
//
//  }
//
//}
