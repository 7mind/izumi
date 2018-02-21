package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.model.common.{Field, Generic, Primitive, UserType}
import com.github.pshirshov.izumi.idealingua.model.finaldef._

object Model01 {
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

  val domain: DomainDefinition = DomainDefinition(DomainId(Seq("izumi", "test"), "domain01"), Seq(
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
      DefMethod.RPCMethod("createUser", DefMethod.Signature(Seq(testInterfaceId), Seq(testInterfaceId)))
    ))
  ))
}
