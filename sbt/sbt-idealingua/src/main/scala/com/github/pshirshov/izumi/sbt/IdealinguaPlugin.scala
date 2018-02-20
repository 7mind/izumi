package com.github.pshirshov.izumi.sbt

import sbt._
import Keys._
import com.github.pshirshov.izumi.idealingua.model
import com.github.pshirshov.izumi.idealingua.model.common.{Field, Primitive}
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition}
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.IDLSuccess
import plugins._
import sbt.internal.util.ConsoleLogger

object Model02 {
  val if1Id = model.common.UserType.parse("izumi.test.TestInterface1").toInterface
  val if2Id = model.common.UserType.parse("izumi.test.TestInterface2").toInterface
  val if3Id = model.common.UserType.parse("izumi.test.TestInterface3").toInterface
  val dto1Id = model.common.UserType.parse("izumi.test.DTO1").toDTO

  val if1 = FinalDefinition.Interface(if1Id, Seq(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt32, "if1Field_inherited")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), Seq.empty)

  val if2 = FinalDefinition.Interface(if2Id, Seq(
    Field(Primitive.TInt64, "if2Field")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), Seq.empty)

  val if3 = FinalDefinition.Interface(if3Id, Seq(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt64, "if3Field")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), Seq(if1Id))


  val dto1 = FinalDefinition.DTO(
    dto1Id
    , Seq(if2Id, if3Id))

  val domain: DomainDefinition = DomainDefinition("testDomain", Seq(
    if1
    , if2
    , if3
    , dto1
  ), Seq.empty)
}


object IdealinguaPlugin extends AutoPlugin {
  private val logger: ConsoleLogger = ConsoleLogger()

  override def requires = JvmPlugin

  override lazy val projectSettings = Seq(
    sourceGenerators in Compile += Def.task {
      val toCompile = Seq(Model02.domain)

      val target = (sourceManaged in Compile).value.toPath
      if (toCompile.nonEmpty) {
        logger.info(s"""Going to compile the following models: ${toCompile.map(_.id).mkString(",")}""")
      }
      toCompile.flatMap {
        domain =>
          logger.info(s"Compiling model ${domain.id} into $target...")
          val compiler = new IDLCompiler(domain)
          compiler.compile(target) match {
            case s: IDLSuccess =>
              logger.debug(s"Model ${domain.id} produces ${s.paths.size} source files...")
              s.paths.map(_.toFile)
            case _ =>
              throw new IllegalStateException(s"Cannot compile model ${domain.id}")
          }
      }
    }.taskValue
  )

  object autoImport {
    val IdealinguaPlugin = com.github.pshirshov.izumi.sbt.IdealinguaPlugin
  }

}
