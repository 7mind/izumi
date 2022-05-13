package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawEntrypointParams, RawFlag, RawRoleParams, RawValue}
import org.scalatest.wordspec.AnyWordSpec

class CliParserTest extends AnyWordSpec {

  "CLI parser" should {
    "parse args" in {
      val v1 = RawAppArgs(
        RawEntrypointParams(Vector(RawFlag("help")), Vector(RawValue("x", "y"), RawValue("logs", "json"))),
        Vector(
          RawRoleParams("role1", RawEntrypointParams(Vector.empty, Vector(RawValue("config", "xxx"))), Vector("arg1", "arg2")),
          RawRoleParams("role2", RawEntrypointParams.empty, Vector.empty),
        ),
      )
      val v2 = RawAppArgs(
        RawEntrypointParams(Vector(RawFlag("help")), Vector(RawValue("x", "y"), RawValue("logs", "json"))),
        Vector(
          RawRoleParams("role1", RawEntrypointParams(Vector.empty, Vector(RawValue("config", "xxx"))), Vector("arg1", "arg2", "--yyy=zzz")),
          RawRoleParams("role2", RawEntrypointParams.empty, Vector.empty),
        ),
      )
      val v3 = RawAppArgs(
        RawEntrypointParams(Vector(RawFlag("help")), Vector(RawValue("x", "y"), RawValue("logs", "json"))),
        Vector(
          RawRoleParams("role1", RawEntrypointParams(Vector.empty, Vector.empty), Vector("--config=xxx", "arg1", "arg2", "--yyy=zzz")),
          RawRoleParams("role2", RawEntrypointParams.empty, Vector.empty),
        ),
      )
      val v4 = RawAppArgs(
        RawEntrypointParams(Vector(RawFlag("x"), RawFlag("x")), Vector(RawValue("x", "y"))),
        Vector(RawRoleParams("role1", RawEntrypointParams(Vector(RawFlag("x"), RawFlag("x")), Vector(RawValue("x", "y"), RawValue("xx", "yy"))), Vector.empty)),
      )
      assert(new CLIParserImpl.parse(Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", ":role2")) == Right(v1))
      assert(new CLIParserImpl.parse(Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")) == Right(v2))
      assert(new CLIParserImpl.parse(Array("--help", "--x=y", "--logs=json", ":role1", "--", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")) == Right(v3))
      assert(new CLIParserImpl.parse(Array("-x", "-x", "y", "-x", ":role1", "-x", "-x", "y", "-x", "--xx=yy")) == Right(v4))

      assert(new CLIParserImpl.parse(Array("-x")).toOption.exists(_.globalParameters.flags.head.name == "x"))
      assert(new CLIParserImpl.parse(Array("-x", "value")).toOption.exists(_.globalParameters.values.head == RawValue("x", "value")))
      assert(new CLIParserImpl.parse(Array("--x", "value")).isLeft)
      assert(new CLIParserImpl.parse(Array("--x=value")).toOption.exists(_.globalParameters.values.head == RawValue("x", "value")))
      assert(
        new CLIParserImpl.parse(Array(":init", "./tmp")) == Right(
          RawAppArgs(RawEntrypointParams.empty, Vector(RawRoleParams("init", RawEntrypointParams.empty, Vector("./tmp"))))
        )
      )
      assert(
        new CLIParserImpl.parse(Array(":init", "--target=./tmp")) == Right(
          RawAppArgs(RawEntrypointParams.empty, Vector(RawRoleParams("init", RawEntrypointParams(Vector.empty, Vector(RawValue("target", "./tmp"))), Vector.empty)))
        )
      )
      assert(
        new CLIParserImpl.parse(Array(":init", "-t", "./tmp")) == Right(
          RawAppArgs(RawEntrypointParams.empty, Vector(RawRoleParams("init", RawEntrypointParams(Vector.empty, Vector(RawValue("t", "./tmp"))), Vector.empty)))
        )
      )
    }
  }

}
