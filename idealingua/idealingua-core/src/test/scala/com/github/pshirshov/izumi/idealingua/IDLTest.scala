package com.github.pshirshov.izumi.idealingua

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef
import com.github.pshirshov.izumi.idealingua.model.finaldef._
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl
import org.scalatest.WordSpec

import scala.tools.nsc.MainClass


class IDLTest extends WordSpec {

  val userIdTypeId = model.common.UserType.parse("izumi.test.UserId").toAlias
  val enumId = model.common.UserType.parse("izumi.test.AnEnum").toEnum
  val testInterfaceId = model.common.UserType.parse("izumi.test.TestInterface").toInterface

  val testValIdentifier = model.common.UserType.parse("izumi.test.TestValIdentifer").toIdentifier
  val testIdentifier = model.common.UserType.parse("izumi.test.TestIdentifer").toIdentifier
  val serviceIdentifier = model.common.UserType.parse("izumi.test.UserService").toService

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

  val domain: DomainDefinition = DomainDefinition("testDomain", Seq(
    FinalDefinition.Alias(userIdTypeId, Primitive.TString)
    , FinalDefinition.Enumeration(enumId, List("VALUE1", "VALUE2"))
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
      DefMethod.RPCMethod("createUser", finaldef.Signature(Seq(testInterfaceId), Seq(testInterfaceId)))
    ))
  ))

  "IDL rendered" should {
    "be able to produce scala source code" in {
      assert(compiles(domain))
    }

    
  }

  private def compiles(d: DomainDefinition): Boolean = {
    val modules = new FinalTranslatorScalaImpl().translate(d)

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

    println(files.toList)
    assert(files.toSet.size == files.size)

    {
      import scala.tools.nsc.{Global, Settings}
      val settings = new Settings()
      settings.usejavacp.value = true
      val g = new Global(settings)
      val run = new g.Run
      run.compile(files.toList)
      run.runIsAt(run.jvmPhase.next)
    }

    //      {
    //        import scala.tools.nsc.Settings
    //        import scala.tools.nsc.{Global, Settings}
    //
    //        val settings: Settings = new Settings()
    //        settings.usejavacp.value = true
    //
    //        val global = new Global(settings)
    //        new MainClass().newCompiler().newCompilationUnit("object Test {}", "test.scala")
    //      }
  }
}
