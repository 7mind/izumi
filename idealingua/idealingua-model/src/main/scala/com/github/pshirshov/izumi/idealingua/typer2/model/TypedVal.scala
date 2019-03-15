package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawConstMeta

sealed trait TypedVal {
  def ref: IzTypeReference
}

object TypedVal {

  sealed trait TypedValScalar extends TypedVal

  final case class TCInt(value: Int) extends TypedValScalar {
    override def ref: IzTypeReference = IzTypeReference.Scalar(IzType.BuiltinScalar.TInt32.id)
  }

  final case class TCLong(value: Long) extends TypedValScalar {
    override def ref: IzTypeReference = IzTypeReference.Scalar(IzType.BuiltinScalar.TInt64.id)
  }

  final case class TCFloat(value: Double) extends TypedValScalar {
    override def ref: IzTypeReference = IzTypeReference.Scalar(IzType.BuiltinScalar.TDouble.id)
  }

  final case class TCString(value: String) extends TypedValScalar {
    override def ref: IzTypeReference = IzTypeReference.Scalar(IzType.BuiltinScalar.TString.id)
  }

  final case class TCBool(value: Boolean) extends TypedValScalar {
    override def ref: IzTypeReference = IzTypeReference.Scalar(IzType.BuiltinScalar.TBool.id)
  }

  final case class TCValue(value: TypedVal, ref: IzTypeReference) extends TypedVal

  final case class TCList(value: List[TypedVal], ref: IzTypeReference) extends TypedVal

  final case class TCObject(value: Map[String, TypedVal], ref: IzTypeReference) extends TypedVal
}

case class TypedConstId(domainId: DomainId, scope: String, name: String)

case class TypedConst(id: TypedConstId, value: TypedVal, meta: RawConstMeta)
