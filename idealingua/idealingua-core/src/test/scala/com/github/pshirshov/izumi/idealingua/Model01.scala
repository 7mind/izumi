package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.{Generic, IndefiniteId, Primitive}
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Service.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il._

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


  final val testValStructure = List(Field(userIdTypeId, "userId"))
  final val testIdStructure = List(Field(userIdTypeId, "userId"), Field(Primitive.TString, "context"))
  final val testIdObject = IndefiniteId.parse("izumi.test.domain01.TestObject").toDTO

  final val domain: DomainDefinition = DomainDefinition(DomainId(Seq("izumi", "test"), "domain01"), Seq(
    ILAst.Alias(userIdTypeId, Primitive.TString)
    , ILAst.Enumeration(enumId, List("VALUE1", "VALUE2"))
    , ILAst.Identifier(testValIdentifier, testValStructure)
    , ILAst.Identifier(testIdentifier, testIdStructure)
    , ILAst.Interface(
      testInterfaceId
      , testInterfaceFields
      , Super.empty
    )
    , ILAst.DTO(
      testIdObject
      , Super.interfaces(List(testInterfaceId))
    )
  ), List(
    Service(serviceIdentifier, List(
      DefMethod.RPCMethod("createUser", DefMethod.Signature(List(testInterfaceId), List(testInterfaceId)))
    ))
  ), Map.empty)
}
