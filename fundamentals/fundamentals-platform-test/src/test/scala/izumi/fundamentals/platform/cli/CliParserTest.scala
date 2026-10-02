package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.model.{EntrypointArgs, RawFlag, RawValue, RoleAppArgs, RoleArgs}
import org.scalatest.wordspec.AnyWordSpec

class CliParserTest extends AnyWordSpec {

  def mkParser() = new CLIParserImpl(new MultiModalArgsParserImpl(), new SubArgsParserImpl())

  "CLI parser" should {
    "parse args" in {

      val a1 = Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", ":role2")
      val a2 = Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")
      val a3 = Array("--help", "--x=y", "--logsjson", ":role1", "--", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")
      val a4 = Array("-x", "-aa", "bb", "-x", ":role1", "-x", "-x", "y", "-x", "--xx=yy")

      val v1 = RoleAppArgs(
        EntrypointArgs(Vector("--help", "--x=y", "--logs=json"), Vector(RawFlag("help")), Vector(RawValue("x", "y"), RawValue("logs", "json")), Vector.empty),
        Vector(
          RoleArgs("role1", EntrypointArgs(Vector("--config=xxx", "arg1", "arg2"), Vector.empty, Vector(RawValue("config", "xxx")), Vector("arg1", "arg2"))),
          RoleArgs("role2", EntrypointArgs.empty),
        ),
      )
      val v2 = RoleAppArgs(
        EntrypointArgs(Vector("--help", "--x=y", "--logs=json"), Vector(RawFlag("help")), Vector(RawValue("x", "y"), RawValue("logs", "json")), Vector.empty),
        Vector(
          RoleArgs(
            "role1",
            EntrypointArgs(
              Vector("--config=xxx", "arg1", "arg2", "--yyy=zzz"),
              Vector.empty,
              Vector(RawValue("config", "xxx"), RawValue("yyy", "zzz")),
              Vector("arg1", "arg2"),
            ),
          ),
          RoleArgs("role2", EntrypointArgs.empty),
        ),
      )
      val v3 = RoleAppArgs(
        EntrypointArgs(Vector("--help", "--x=y", "--logsjson"), Vector(RawFlag("help"), RawFlag("logsjson")), Vector(RawValue("x", "y")), Vector.empty),
        Vector(
          RoleArgs(
            "role1",
            EntrypointArgs(
              Vector("--", "--config=xxx", "arg1", "arg2", "--yyy=zzz"),
              Vector.empty,
              Vector.empty,
              Vector("--config=xxx", "arg1", "arg2", "--yyy=zzz"),
            ),
          ),
          RoleArgs("role2", EntrypointArgs.empty),
        ),
      )
      val v4 = RoleAppArgs(
        EntrypointArgs(Vector("-x", "-aa", "bb", "-x"), Vector(RawFlag("x"), RawFlag("x")), Vector(RawValue("aa", "bb")), Vector.empty),
        Vector(
          RoleArgs(
            "role1",
            EntrypointArgs(Vector("-x", "-x", "y", "-x", "--xx=yy"), Vector(RawFlag("x"), RawFlag("x")), Vector(RawValue("x", "y"), RawValue("xx", "yy")), Vector.empty),
          )
        ),
      )

      val p1 = mkParser().parse(a1)
      val p2 = mkParser().parse(a2)
      val p3 = mkParser().parse(a3)
      val p4 = mkParser().parse(a4)
      assert(p1 == Right(v1))
      assert(p2 == Right(v2))
      assert(p3 == Right(v3))
      assert(p4 == Right(v4))

      assert(mkParser().parse(Array("-x")).toOption.exists(_.globalParameters.flags.head.name == "x"))
      assert(mkParser().parse(Array("-x", "value")).toOption.exists(_.globalParameters.values.head == RawValue("x", "value")))
//      assert(mkParser().parse(Array("--x", "value")).isLeft)

      assert(mkParser().parse(Array("--x=value")).toOption.exists(_.globalParameters.values.head == RawValue("x", "value")))
      assert(
        mkParser().parse(Array(":init", "./tmp")) == Right(
          RoleAppArgs(
            EntrypointArgs.empty,
            Vector(RoleArgs("init", EntrypointArgs(Vector("./tmp"), Vector.empty, Vector.empty, Vector("./tmp")))),
          )
        )
      )
      assert(
        mkParser().parse(Array(":init", "--target=./tmp")) == Right(
          RoleAppArgs(
            EntrypointArgs.empty,
            Vector(RoleArgs("init", EntrypointArgs(Vector("--target=./tmp"), Vector.empty, Vector(RawValue("target", "./tmp")), Vector.empty))),
          )
        )
      )
      assert(
        mkParser().parse(Array(":init", "-t", "./tmp")) == Right(
          RoleAppArgs(
            EntrypointArgs.empty,
            Vector(RoleArgs("init", EntrypointArgs(Vector("-t", "./tmp"), Vector.empty, Vector(RawValue("t", "./tmp")), Vector.empty))),
          )
        )
      )
    }
  }

}
