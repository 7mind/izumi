package com.github.pshirshov.izumi.idealingua.translator

import java.nio.file.Path

import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition}

class ModelLoader(source: Path) {
  def load(): Seq[DomainDefinition] = {
    Seq(ModelLoader.DummyModel.domain)
  }
}

object ModelLoader {
  object DummyModel {
    val if1Id = UserType.parse("izumi.test.TestInterface1").toInterface
    val if2Id = UserType.parse("izumi.test.TestInterface2").toInterface
    val if3Id = UserType.parse("izumi.test.TestInterface3").toInterface
    val dto1Id = UserType.parse("izumi.test.DTO1").toDTO

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
}
