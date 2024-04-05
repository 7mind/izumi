package izumi.distage.config

import com.github.pshirshov.configapp.TestConfigReaders
import izumi.distage.config.codec.ConfigMetaType
import izumi.distage.config.codec.ConfigMetaType.*
import izumi.distage.config.model.ConfTag
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec

final class ConfigValueTest extends AnyWordSpec {

  def getConfTag(plannerInput: PlannerInput): ConfTag = {
    val tags = plannerInput.bindings.iterator.flatMap {
      b => b.tags.collect { case c: ConfTag => c }
    }.toSeq
    assert(tags.size == 1)
    tags.head
  }

  "Config fields meta" should {

    "properly derive config maps" in {
      getConfTag(TestConfigReaders.mapDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("mymap").isInstanceOf[TMap])
        case _ =>
          fail()
      }
    }

    "properly derive config lists" in {
      getConfTag(TestConfigReaders.listDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("mylist").isInstanceOf[TList])
        case _ =>
          fail()
      }
    }

    "be as expected for config options" in {
      getConfTag(TestConfigReaders.optDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("optInt").isInstanceOf[TOption])
          assert(c.fields.toMap.apply("optCustomObject").isInstanceOf[TOption])
        case _ =>
          fail()
      }
    }

    "be unknown for config tuples" in {
      getConfTag(TestConfigReaders.tupleDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("tuple").isInstanceOf[TUnknown])
        case _ =>
          fail()
      }
    }

    "be unknown for custom codecs" in {
      getConfTag(TestConfigReaders.customCodecDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("customObject").isInstanceOf[TUnknown])

          c.fields.toMap.apply("mapCustomObject") match {
            case m: TMap =>
              assert(m.valueType.isInstanceOf[TUnknown])
            case _ =>
              fail()
          }

          c.fields.toMap.apply("mapListCustomObject") match {
            case m: TMap =>
              m.valueType match {
                case l: TList =>
                  assert(l.tpe.isInstanceOf[TUnknown])
                case o =>
                  fail(o.toString)
              }
            case _ =>
              fail()
          }

        case _ =>
          fail()
      }
    }

    "be as expected for backticks" in {
      getConfTag(TestConfigReaders.backticksDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("boo-lean").isInstanceOf[TBasic])
        case _ =>
          fail()
      }
    }

    "be as expected for case classes with private fields" in {
      getConfTag(TestConfigReaders.privateFieldsCodecDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("private-custom-field-name").isInstanceOf[TBasic])
        case _ =>
          fail()
      }
    }

    "be as expected for case classes with partially private fields" in {
      getConfTag(TestConfigReaders.partiallyPrivateFieldsCodecDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          assert(c.fields.toMap.apply("private-custom-field-name").isInstanceOf[TBasic])
          assert(c.fields.toMap.apply("publicField").isInstanceOf[TBasic])
        case _ =>
          fail()
      }

    }

    "be as expected for sealed traits" in {
      getConfTag(TestConfigReaders.sealedDefinition).tpe match {
        case c: ConfigMetaType.TCaseClass =>
          val sealedTrait = c.fields.toMap.apply("sealedTrait1").asInstanceOf[TSealedTrait]
          assert(sealedTrait.branches.toMap.apply("CaseClass1").isInstanceOf[TCaseClass])
          assert(sealedTrait.branches.toMap.apply("CaseClass2").isInstanceOf[TCaseClass])
        case _ =>
          fail()
      }
    }

  }

}
