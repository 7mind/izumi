package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId}

object Builtins extends TypePlane {

  import IzType.BuiltinGeneric._
  import IzType.BuiltinScalar._

  def resolve(typeId: IzName): Option[IzType] = {
    mappingAll.get(IzTypeId.BuiltinTypeId(typeId))
  }


  lazy val scalars: Set[IzType.BuiltinScalar] = Set(
    TBool,
    TString,
    TInt8,
    TInt16,
    TInt32,
    TInt64,
    TUInt8,
    TUInt16,
    TUInt32,
    TUInt64,
    TFloat,
    TDouble,
    TUUID,
    TBLOB,
    TTs,
    TTsTz,
    TTsU,
    TTime,
    TDate,
  )

  lazy val specials: Set[IzType.BuiltinScalar] = Set(
    TErr,
    TAny,
  )

  lazy val generics: Set[IzType.BuiltinGeneric] = Set(
    TList,
    TMap,
    TOption,
    TSet,
    TEither,
  )

  lazy val all: Set[IzType.BuiltinType] = scalars ++ generics ++ specials

  lazy val mappingAll: Map[IzTypeId.BuiltinTypeId, IzType.BuiltinType] = makeMapping(all)
  lazy val mappingSpecials: Map[IzTypeId.BuiltinTypeId, IzType.BuiltinType] = makeMapping(specials.toSet)
  lazy val mappingGenerics: Map[IzTypeId.BuiltinTypeId, IzType.BuiltinType] = makeMapping(generics.toSet)
  lazy val mappingScalars: Map[IzTypeId.BuiltinTypeId, IzType.BuiltinType] = makeMapping(scalars.toSet)


  private def makeMapping(a: Set[IzType.BuiltinType]): Map[IzTypeId.BuiltinTypeId, IzType.BuiltinType] = {
    a
      .flatMap {
        d =>
          d.names.map {
            name =>
              IzTypeId.BuiltinTypeId(name) -> d
          }
      }
      .toMap
  }
}
