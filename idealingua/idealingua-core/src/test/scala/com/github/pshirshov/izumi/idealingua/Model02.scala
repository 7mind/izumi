package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.{Field, Primitive}
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition}

object Model02 {
  val if1Id = model.common.UserType.parse("izumi.test.TestInterface1").toInterface
  val if2Id = model.common.UserType.parse("izumi.test.TestInterface2").toInterface
  val if3Id = model.common.UserType.parse("izumi.test.TestInterface3").toInterface
  val dto1Id = model.common.UserType.parse("izumi.test.DTO1").toDTO

  val if1 = FinalDefinition.Interface(if1Id, Seq(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt32, "if1Field_inherited")
  ), Seq.empty)

  val if2 = FinalDefinition.Interface(if2Id, Seq(
    Field(Primitive.TInt64, "if2Field")
  ), Seq.empty)

  val if3 = FinalDefinition.Interface(if3Id, Seq(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt64, "if3Field")
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
