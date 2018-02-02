package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.finaldef.{DefMethod, DomainDefinition, FinalDefinition, Service}
import com.github.pshirshov.izumi.idealingua.model.{Field, TypeId, TypeName}
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl
import org.scalatest.WordSpec

//import scala.io.Source
//import scala.tools
//import scala.tools.nsc.{GenericRunnerSettings, Global, Settings}
//import scala.tools.nsc
//import scala.tools.nsc.interpreter.IMain


class BootstrapTest extends WordSpec {

  val userIdTypeId = model.UserType.parse("org.test.UserId")
  val testInterfaceId = model.UserType.parse("org.test.TestInterface")

  val testValIdentifier = model.UserType.parse("org.test.TestValIdentifer")
  val testIdentifier = model.UserType.parse("org.test.TestIdentifer")
  val serviceIdentifier = model.UserType.parse("org.test.UserService")

  val testInterfaceFields = Seq(
    Field(userIdTypeId, "userId")
    , Field(TypeId.TInt32, "accountBalance")
    , Field(TypeId.TInt64, "latestLogin")
  )

  val testValStructure = Seq(Field(userIdTypeId, "userId"))
  val testIdStructure = Seq(Field(userIdTypeId, "userId"), Field(TypeId.TString, "context"))

  val domain: DomainDefinition = DomainDefinition(Seq(
    FinalDefinition.Alias(userIdTypeId, TypeId.TString)
    , FinalDefinition.Identifier(testValIdentifier, testValStructure)
    , FinalDefinition.Identifier(testIdentifier, testIdStructure)
    , FinalDefinition.Interface(
      testInterfaceId
      , testInterfaceFields
      , Seq.empty
    )
    , FinalDefinition.DTO(
      model.UserType(Seq("org", "test"), TypeName("TestObject")),
      Seq(testInterfaceId))
  ), Seq(
    Service(serviceIdentifier, Seq(
      DefMethod.RPCMethod("createUser", userIdTypeId, testInterfaceId)
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


      modules.foreach {
        module =>
            println((module.id.path :+ module.id.name).mkString("/"))
            println(module.content)
            println()
        //Files.write(Paths.get(module.id.name), module.content.getBytes(StandardCharsets.UTF_8))
      }

//      val g = new Global(new Settings())
//      val run = new g.Run
//      run.compile(modules.map(_.id.name).toList)

      //      val settings: Settings = new GenericRunnerSettings(s => {println(s)})
      //      val global = new tools.nsc.interactive.Global(settings, new scala.tools.nsc.reporters.ConsoleReporter(settings))
      //      global.
      //new scala.tools.nsc.MainClass().newCompiler().newCompilationUnit("object Test {}", "test.scala")
    }
  }

}
