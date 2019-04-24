package com.github.pshirshov.izumi.fundamentals.platform.cli

import org.scalatest.WordSpec

class CliParserTest extends WordSpec {

  "CLI parser" should {
    "parse args" in {
      val v1: RoleAppArguments = RoleAppArguments(Parameters(Vector(Flag("help")), Vector(Value("x", "y"), Value("logs", "json"))), Vector(RoleArg("role1", Parameters(Vector.empty, Vector(Value("config", "xxx"))), Vector("arg2")), RoleArg("role2", Parameters.empty, Vector.empty)))
      val v2: RoleAppArguments = RoleAppArguments(Parameters(Vector(Flag("help")),Vector(Value("x","y"), Value("logs","json"))),Vector(RoleArg("role1",Parameters(Vector.empty,Vector(Value("config","xxx"))),Vector("arg2", "--yyy=zzz")), RoleArg("role2",Parameters.empty,Vector.empty)))
      val v3: RoleAppArguments = RoleAppArguments(Parameters(Vector(Flag("help")),Vector(Value("x","y"), Value("logs","json"))),Vector(RoleArg("role1",Parameters(Vector.empty,Vector.empty), Vector("--config=xxx", "arg1", "arg2", "--yyy=zzz")), RoleArg("role2",Parameters.empty,Vector.empty)))
      val v4: RoleAppArguments = RoleAppArguments(Parameters(Vector(Flag("x"), Flag("x")),Vector(Value("x","y"))),Vector(RoleArg("role1",Parameters(Vector(Flag("x"), Flag("x")),Vector(Value("x","y"), Value("xx","yy"))),Vector.empty)))
      assert(new CLIParser().parse(Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", ":role2")) == Right(v1))
      assert(new CLIParser().parse(Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")) == Right(v2))
      assert(new CLIParser().parse(Array("--help", "--x=y", "--logs=json", ":role1", "--", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")) == Right(v3))
      assert(new CLIParser().parse(Array("-x", "-x", "y", "-x", ":role1", "-x", "-x", "y", "-x", "--xx=yy")) == Right(v4))
    }
  }


}

