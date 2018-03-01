package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.{Field, Primitive}
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId, FinalDefinition}

object Model02 {
  final val if1Id = model.common.Indefinite.parse("izumi.test.domain02.TestInterface1").toInterface
  final val if2Id = model.common.Indefinite.parse("izumi.test.domain02.TestInterface2").toInterface
  final val if3Id = model.common.Indefinite.parse("izumi.test.domain02.TestInterface3").toInterface
  final val dto1Id = model.common.Indefinite.parse("izumi.test.domain02.DTO1").toDTO

  final val if1 = FinalDefinition.Interface(if1Id, List(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt32, "if1Field_inherited")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), List.empty, List.empty)

  final val if2 = FinalDefinition.Interface(if2Id, List(
    Field(Primitive.TInt64, "if2Field")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), List.empty, List.empty)

  final val if3 = FinalDefinition.Interface(if3Id, List(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt64, "if3Field")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), List(if1Id), List.empty)


  final val dto1 = FinalDefinition.DTO(
    dto1Id
    , List(if2Id, if3Id)
    , List.empty
  )

  final val domain: DomainDefinition = DomainDefinition(DomainId(Seq("izumi", "test"), "domain02"), Seq(
    if1
    , if2
    , if3
    , dto1
  ), Seq.empty, Map.empty)
}
