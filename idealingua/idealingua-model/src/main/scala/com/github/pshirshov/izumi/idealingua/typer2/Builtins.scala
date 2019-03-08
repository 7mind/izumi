package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzName
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId}

object Builtins extends TypePlane {

  import IzType.BuiltinGeneric._
  import IzType.BuiltinScalar._

  def resolve(typeId: IzName): Option[IzType] = {
    mapping.get(IzTypeId.BuiltinType(typeId))
  }


  private lazy val scalars: Seq[IzType.BuiltinScalar] = Seq(
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
    TErr,
  )

  private lazy val generics: Seq[IzType.BuiltinGeneric] = Seq(
    TList,
    TMap,
    TOption,
    TSet,
    TEither,
  )

  private lazy val all: Seq[IzType.BuiltinType] = scalars ++ generics

  lazy val mapping: Map[IzTypeId.BuiltinType, IzType.BuiltinType] = all
    .flatMap {
      d =>
        d.names.map {
          name =>
            IzTypeId.BuiltinType(name) -> d
        }
    }
    .toMap
}
