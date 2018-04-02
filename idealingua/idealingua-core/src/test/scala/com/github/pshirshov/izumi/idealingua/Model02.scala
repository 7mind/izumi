package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.Primitive
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

object Model02 {
  def struct(fields: List[Field]): Structure = {
    struct(fields, List.empty)
  }

  def struct(fields: List[Field], interfaces: List[InterfaceId]): Structure = {
    Structure.interfaces(interfaces).copy(fields = fields)
  }

  final val if1Id = model.common.IndefiniteId.parse("izumi.test.domain02.TestInterface1").toInterface
  final val if2Id = model.common.IndefiniteId.parse("izumi.test.domain02.TestInterface2").toInterface
  final val if3Id = model.common.IndefiniteId.parse("izumi.test.domain02.TestInterface3").toInterface
  final val dto1Id = model.common.IndefiniteId.parse("izumi.test.domain02.DTO1").toDTO

  final val if1 = TypeDef.Interface(if1Id, struct(List(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt32, "if1Field_inherited")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  )))

  final val if2 = TypeDef.Interface(if2Id, struct(List(
    Field(Primitive.TInt64, "if2Field")
    , Field(Primitive.TInt64, "sameField")
    , Field(Primitive.TInt64, "sameEverywhereField")
  )))

  final val if3 = TypeDef.Interface(if3Id, struct(List(
    Field(Primitive.TInt32, "if1Field_overriden")
    , Field(Primitive.TInt64, "if3Field")
    , Field(Primitive.TInt64, "sameEverywhereField")
  ), List(if1Id)))


  final val dto1 = TypeDef.DTO(
    dto1Id
    , Structure.interfaces(List(if2Id, if3Id))
  )

  final val domain: DomainDefinition = DomainDefinition(DomainId(Seq("izumi", "test"), "domain02"), Seq(
    if1
    , if2
    , if3
    , dto1
  ), Seq.empty, Map.empty)
}
