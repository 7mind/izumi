package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.{Generic, IndefiniteId, Primitive}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{TypeDef, _}

object Model01 {
  final val userIdTypeId = model.common.IndefiniteId.parse("izumi.test.domain01.UserId").toAlias
  final val enumId = model.common.IndefiniteId.parse("izumi.test.domain01.AnEnum").toEnum
  final val testInterfaceId = model.common.IndefiniteId.parse("izumi.test.domain01.TestInterface").toInterface

  final val testValIdentifier = model.common.IndefiniteId.parse("izumi.test.domain01.TestValIdentifier").toIdentifier
  final val testIdentifier = model.common.IndefiniteId.parse("izumi.test.domain01.TestIdentifer").toIdentifier
  final val serviceIdentifier = model.common.IndefiniteId.parse("izumi.test.domain01.UserService").toService

  final val testInterfaceFields = List(
    Field(userIdTypeId, "userId")
    , Field(Primitive.TInt32, "accountBalance")
    , Field(Primitive.TInt64, "latestLogin")
    , Field(Generic.TMap(Primitive.TString, Primitive.TString), "keys")
    , Field(Generic.TList(Primitive.TString), "nicknames")
  )


  final val testValStructure = List(PrimitiveField(Primitive.TString, "userId"))
  final val testIdStructure = List(PrimitiveField(Primitive.TString, "userId"), PrimitiveField(Primitive.TString, "context"))
  final val testIdObject = IndefiniteId.parse("izumi.test.domain01.TestObject").toDTO

  final val domain: DomainDefinition = DomainDefinition(DomainId(Seq("izumi", "test"), "domain01"), Seq(
    TypeDef.Alias(userIdTypeId, Primitive.TString)
    , TypeDef.Enumeration(enumId, List("VALUE1", "VALUE2"))
    , TypeDef.Identifier(testValIdentifier, testValStructure)
    , TypeDef.Identifier(testIdentifier, testIdStructure)
    , TypeDef.Interface(
      testInterfaceId
      , Structure(testInterfaceFields, List.empty, Super.empty)
    )
    , TypeDef.DTO(
      testIdObject
      , Structure.interfaces(List(testInterfaceId))
    )
  ), List(
    Service(serviceIdentifier, List(
      DefMethod.DeprecatedRPCMethod("createUser", DefMethod.DeprecatedSignature(List(testInterfaceId), List(testInterfaceId)))
    ))
  ), Map.empty)
}
