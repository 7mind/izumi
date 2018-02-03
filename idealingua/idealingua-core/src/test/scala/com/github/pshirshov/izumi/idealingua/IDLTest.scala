package com.github.pshirshov.izumi.idealingua

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DefMethod, DomainDefinition, FinalDefinition, Service}
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl
import org.scalatest.{WordSpec, path}


class IDLTest extends WordSpec {

  val userIdTypeId = model.common.UserType.parse("izumi.test.UserId").toAlias
  val testInterfaceId = model.common.UserType.parse("izumi.test.TestInterface").toInterface

  val testValIdentifier = model.common.UserType.parse("izumi.test.TestValIdentifer").toIdentifier
  val testIdentifier = model.common.UserType.parse("izumi.test.TestIdentifer").toIdentifier
  val serviceIdentifier = model.common.UserType.parse("izumi.test.UserService").toIdentifier

  val testInterfaceFields = Seq(
    Field(userIdTypeId, "userId")
    , Field(Primitive.TInt32, "accountBalance")
    , Field(Primitive.TInt64, "latestLogin")
    , Field(Generic.TMap(Primitive.TString, Primitive.TString), "keys")
    , Field(Generic.TList(Primitive.TString), "nicknames")
  )


  val testValStructure = Seq(Field(userIdTypeId, "userId"))
  val testIdStructure = Seq(Field(userIdTypeId, "userId"), Field(Primitive.TString, "context"))
  val testIdObject = UserType.parse("izumi.test.TestObject").toDTO

  val domain: DomainDefinition = DomainDefinition(Seq(
    FinalDefinition.Alias(userIdTypeId, Primitive.TString)
    , FinalDefinition.Identifier(testValIdentifier, testValStructure)
    , FinalDefinition.Identifier(testIdentifier, testIdStructure)
    , FinalDefinition.Interface(
      testInterfaceId
      , testInterfaceFields
      , Seq.empty
    )
    , FinalDefinition.DTO(
      testIdObject
      , Seq(testInterfaceId))
  ), Seq(
    Service(serviceIdentifier, Seq(
      DefMethod.RPCMethod("createUser", testIdObject, testInterfaceId)
    ))
  ))

  "IDL rendered" should {
    "be able to produce scala source code" in {
      val modules = new FinalTranslatorScalaImpl().translate(domain)


      //      modules.foreach {
      //        m =>
      //
      //
      //          import reflect.runtime.currentMirror
      //          import tools.reflect.ToolBox
      //          val toolbox = currentMirror.mkToolBox()
      //          import toolbox.u._
      //
      //          //val fileContents = Source.fromString() // fromFile(implFilename).getLines.mkString("\n")
      //          println(m.content)
      //          val tree = toolbox.parse(m.content)
      //          println(tree)
      ////          val compiledCode = toolbox.compile(tree)
      ////          println(compiledCode())
      //      }


      val top = Seq("target", "idl-" + System.currentTimeMillis())
      val files = modules.map {
        module =>

          val path = top ++ module.id.path :+ module.id.name
          val mpath = path.mkString("/")
          println(mpath)
          println(module.content)
          println()
          val xpath = Paths.get(path.head, path.tail: _*)
          xpath.getParent.toFile.mkdirs()
          Files.write(xpath, module.content.getBytes(StandardCharsets.UTF_8))
          mpath
      }
      import scala.io.Source
      import scala.tools
      import scala.tools.nsc.{GenericRunnerSettings, Global, Settings}
      import scala.tools.nsc
      import scala.tools.nsc.interpreter.IMain

      val settings = new Settings()
      settings.usejavacp.value = true
      println(settings.classpath.value)
      val g = new Global(settings)

      println(files.toList)
      assert(files.toSet.size == files.size)
      val run = new g.Run
      run.compile(files.toList)

      //            val settings: Settings = new GenericRunnerSettings(s => {println(s)})
      //            val global = new tools.nsc.interactive.Global(settings, new scala.tools.nsc.reporters.ConsoleReporter(settings))
      //            global.
      //      new scala.tools.nsc.MainClass().newCompiler().newCompilationUnit("object Test {}", "test.scala")
    }
  }

}
