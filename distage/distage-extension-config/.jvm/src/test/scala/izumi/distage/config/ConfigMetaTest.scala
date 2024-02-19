package izumi.distage.config

import com.github.pshirshov.configapp.TestConfigReaders
import izumi.distage.config.codec.ConfigMeta
import izumi.distage.config.model.ConfTag
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec

final class ConfigMetaTest extends AnyWordSpec {

  def getConfTag(plannerInput: PlannerInput): ConfTag = {
    val tags = plannerInput.bindings.iterator.flatMap {
      b => b.tags.collect { case c: ConfTag => c }
    }.toSeq
    assert(tags.size == 1)
    tags.head
  }

  "Config fields meta" should {

    "be unknown for config maps" in {
      val c = getConfTag(TestConfigReaders.mapDefinition)
      assert(c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(Seq("mymap" -> ConfigMeta.ConfigMetaUnknown())))
    }

    "be unknown for config lists" in {
      val c = getConfTag(TestConfigReaders.listDefinition)
      assert(c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(Seq("mylist" -> ConfigMeta.ConfigMetaUnknown())))
    }

    "be as expected for config options" in {
      val c = getConfTag(TestConfigReaders.optDefinition)
      assert(
        c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(
          Seq(
            "optInt" -> ConfigMeta.ConfigMetaUnknown(),
            "optCustomObject" -> ConfigMeta.ConfigMetaCaseClass(Seq("value" -> ConfigMeta.ConfigMetaUnknown())),
          )
        )
      )
    }

    "be unknown for config tuples" in {
      val c = getConfTag(TestConfigReaders.tupleDefinition)
      assert(c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(Seq("tuple" -> ConfigMeta.ConfigMetaUnknown())))
    }

    "be unknown for custom codecs" in {
      val c = getConfTag(TestConfigReaders.customCodecDefinition)
      assert(
        c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(
          Seq(
            "customObject" -> ConfigMeta.ConfigMetaUnknown(),
            "mapCustomObject" -> ConfigMeta.ConfigMetaUnknown(),
            "mapListCustomObject" -> ConfigMeta.ConfigMetaUnknown(),
          )
        )
      )
    }

    "be as expected for backticks" in {
      val c = getConfTag(TestConfigReaders.backticksDefinition)

      assert(c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(Seq("boo-lean" -> ConfigMeta.ConfigMetaUnknown())))
    }

    "be as expected for case classes with private fields" in {
      val c = getConfTag(TestConfigReaders.privateFieldsCodecDefinition)

      assert(c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(Seq("private-custom-field-name" -> ConfigMeta.ConfigMetaUnknown())))
    }

    "be as expected for case classes with partially private fields" in {
      val c = getConfTag(TestConfigReaders.partiallyPrivateFieldsCodecDefinition)

      assert(
        c.fieldsMeta == ConfigMeta.ConfigMetaCaseClass(
          Seq(
            "private-custom-field-name" -> ConfigMeta.ConfigMetaUnknown(),
            "publicField" -> ConfigMeta.ConfigMetaUnknown(),
          )
        )
      )
    }

    "be as exptected for sealed traits" in {
      val c = getConfTag(TestConfigReaders.sealedDefinition)

      assert(
        c.fieldsMeta ==
        ConfigMeta.ConfigMetaCaseClass(
          Seq(
            "sealedTrait1" ->
            ConfigMeta.ConfigMetaSealedTrait(
              Set(
                "CaseClass1" -> ConfigMeta.ConfigMetaCaseClass(
                  Seq(
                    "int" -> ConfigMeta.ConfigMetaUnknown(),
                    "string" -> ConfigMeta.ConfigMetaUnknown(),
                    "boolean" -> ConfigMeta.ConfigMetaUnknown(),
                    "sealedTrait2" -> ConfigMeta.ConfigMetaSealedTrait(
                      Set(
                        "Yes" -> ConfigMeta.ConfigMetaCaseClass(Seq()),
                        "No" -> ConfigMeta.ConfigMetaCaseClass(Seq()),
                      )
                    ),
                  )
                ),
                "CaseClass2" -> ConfigMeta.ConfigMetaCaseClass(
                  Seq(
                    "int" -> ConfigMeta.ConfigMetaUnknown(),
                    "boolean" -> ConfigMeta.ConfigMetaUnknown(),
                    "sealedTrait2" -> ConfigMeta.ConfigMetaSealedTrait(
                      Set(
                        "Yes" -> ConfigMeta.ConfigMetaCaseClass(Seq()),
                        "No" -> ConfigMeta.ConfigMetaCaseClass(Seq()),
                      )
                    ),
                  )
                ),
              )
            )
          )
        )
      )
    }

  }

}
