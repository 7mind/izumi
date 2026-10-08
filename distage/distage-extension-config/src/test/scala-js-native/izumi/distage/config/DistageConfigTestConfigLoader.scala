package izumi.distage.config

import io.circe.JsonObject

object DistageConfigTestConfigLoader {
  def loadConfig(name: String): DistageConfigImpl = {
    val jsonString = configJsons.getOrElse(
      name.stripSuffix(".conf"),
      throw new IllegalArgumentException(s"Unknown test config: $name"),
    )

    io.circe.parser.parse(jsonString) match {
      case Right(json) =>
        json.asObject.getOrElse(JsonObject.empty)
      case Left(err) =>
        throw new RuntimeException(s"Failed to parse test config $name: ${err.getMessage}", err)
    }
  }

  private val configJsons: Map[String, String] = Map(
    "map-test" ->
    """{
      |  "MapCaseClass": {
      |    "mymap": {
      |      "service1": { "port": 80, "host": "localhost" },
      |      "service2": { "port": 8080, "host": "localhost" },
      |      "service3": { "port": 8888, "host": "localhost" },
      |      "service4": { "port": 8, "host": "localhost" },
      |      "service5": { "port": 808, "host": "localhost" },
      |      "service6": { "port": 88, "host": "localhost" }
      |    }
      |  }
      |}""".stripMargin,
    "list-test" ->
    """{
      |  "ListCaseClass": {
      |    "mylist": [
      |      [
      |        { "wrap": { "port": 80, "host": "localhost" } },
      |        { "wrap": { "port": 8080, "host": "localhost" } },
      |        { "wrap": { "port": 8888, "host": "localhost" } }
      |      ]
      |    ]
      |  }
      |}""".stripMargin,
    "opt-test" ->
    """{
      |  "OptionCaseClass": {}
      |}""".stripMargin,
    "opt-test-missing" ->
    """{
      |  "OptionCaseClass": {}
      |}""".stripMargin,
    "tuple-test" ->
    """{
      |  "TupleCaseClass": {
      |    "tuple": [1, "two", false, { "Right": { "value": ["r"] } }]
      |  }
      |}""".stripMargin,
    "custom-codec-test" ->
    """{
      |  "CustomCaseClass": {
      |    "customObject": "eaaxacaca",
      |    "mapCustomObject": {
      |      "a": "eaaxacaca",
      |      "b": "a"
      |    },
      |    "mapListCustomObject": {
      |      "x": ["a", "eaaxacaca", "other"]
      |    }
      |  }
      |}""".stripMargin,
    "backticks-test" ->
    """{
      |  "BackticksCaseClass": {
      |    "boo-lean": true
      |  }
      |}""".stripMargin,
    "private-fields-test" ->
    """{
      |  "PrivateCaseClass": {
      |    "private-custom-field-name": "super secret value"
      |  }
      |}""".stripMargin,
    "partially-private-fields-test" ->
    """{
      |  "PartiallyPrivateCaseClass": {
      |    "private-custom-field-name": "super secret value",
      |    "publicField": true
      |  }
      |}""".stripMargin,
    "sealed-test1" ->
    """{
      |  "SealedCaseClass": {
      |    "sealedTrait1": {
      |      "CaseClass1": {
      |        "int": 1,
      |        "string": "1",
      |        "boolean": true,
      |        "sealedTrait2": { "Yes": {} }
      |      }
      |    }
      |  }
      |}""".stripMargin,
    "sealed-test2" ->
    """{
      |  "SealedCaseClass": {
      |    "sealedTrait1": {
      |      "CaseClass2": {
      |        "int": 2,
      |        "boolean": false,
      |        "sealedTrait2": { "No": {} }
      |      }
      |    }
      |  }
      |}""".stripMargin,
  )
}
