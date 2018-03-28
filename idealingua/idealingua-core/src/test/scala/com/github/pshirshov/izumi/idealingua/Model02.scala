package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.Primitive
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.{Field, Super}
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId, ILAst}

object Model02 {
  final val if1Id = model.common.IndefiniteId.parse("izumi.test.domain02.TestInterface1").toInterface
  final val if2Id = model.common.IndefiniteId.parse("izumi.test.domain02.TestInterface2").toInterface
  final val if3Id = model.common.IndefiniteId.parse("izumi.test.domain02.TestInterface3").toInterface
  final val dto1Id = model.common.IndefiniteId.parse("izumi.test.domain02.DTO1").toDTO

  final val if1 = ILAst.Interface(if1Id, List(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt32, "if1Field_inherited")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), Super.empty)

  final val if2 = ILAst.Interface(if2Id, List(
    Field(Primitive.TInt64, "if2Field")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), Super.empty)

  final val if3 = ILAst.Interface(if3Id, List(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt64, "if3Field")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), Super.interfaces(List(if1Id)))


  final val dto1 = ILAst.DTO(
    dto1Id
    , Super.interfaces(List(if2Id, if3Id))
  )

  final val domain: DomainDefinition = DomainDefinition(DomainId(Seq("izumi", "test"), "domain02"), Seq(
    if1
    , if2
    , if3
    , dto1
  ), Seq.empty, Map.empty)
}
