package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.Adt
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{AdtMemberNested, AdtMemberRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzNamespace
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.{BuilderFail, GenericAdtBranchMustBeNamed}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzType, IzTypeId, IzTypeReference}
import com.github.pshirshov.izumi.idealingua.typer2.results._


class AdtSupport(
                  i2: TypedefSupport,
                  resolvers: Resolvers,
                ) {

  import AdtSupport._

  def makeAdt(a: RawTypeDef.Adt): TList = {
    makeAdt(a, Seq.empty).map(_.flatten)
  }

  private def makeAdt(a: RawTypeDef.Adt, subpath: Seq[IzNamespace]): TChain = {
    val id = resolvers.nameToId(a.id, subpath)

    for {
      members <- a.alternatives.map(mapMember(id, subpath)).biAggregate
      adtMembers = members.map(_.member)
      associatedTypes = members.flatMap(_.additional)
    } yield {
      Chain(Adt(id, adtMembers, i2.meta(a.meta)), associatedTypes)
    }
  }

  def mapMember(context: IzTypeId, subpath: Seq[IzNamespace])(member: Member): Either[List[BuilderFail], AdtMemberProducts] = {
    member match {
      case Member.TypeRef(typeId, memberName, m) =>
        val tpe = resolvers.resolve(typeId)
        for {
          name <- tpe match {
            case IzTypeReference.Scalar(mid) =>
              Right(memberName.getOrElse(mid.name.name))
            case IzTypeReference.Generic(_, _, _) =>
              memberName match {
                case Some(value) =>
                  Right(value)
                case None =>
                  Left(List(GenericAdtBranchMustBeNamed(context, typeId, i2.meta(m))))
              }
          }
        } yield {
          AdtMemberProducts(AdtMemberRef(name, tpe, i2.meta(m)), List.empty)
        }

      case Member.NestedDefn(nested) =>
        for {
          tpe <- nested match {
            case n: RawTypeDef.Interface =>
              i2.makeInterface(n, subpath).asChain
            case n: RawTypeDef.DTO =>
              i2.makeDto(n, subpath).asChain
            case n: RawTypeDef.Enumeration =>
              i2.makeEnum(n, subpath).asChain
            case n: RawTypeDef.Alias =>
              i2.makeAlias(n, subpath).asChain
            case n: RawTypeDef.Identifier =>
              i2.makeIdentifier(n, subpath).asChain
            case n: RawTypeDef.Adt =>
              makeAdt(n, subpath :+ IzNamespace(n.id.name))
          }
        } yield {
          AdtMemberProducts(AdtMemberNested(nested.id.name, IzTypeReference.Scalar(tpe.main.id), i2.meta(nested.meta)), tpe.main +: tpe.additional)
        }
    }
  }



}

object AdtSupport {

  case class Chain(main: IzType, additional: List[IzType]) {
    def flatten: List[IzType] = {

      List(main) ++ additional
    }
  }

  case class AdtMemberProducts(member: IzType.model.AdtMember, additional: List[IzType])

  type TChain = Either[List[BuilderFail], Chain]

  implicit class TSingleExt1[T <: IzType](ret: TSingleT[T]) {
    def asChain: TChain = ret.map(r => Chain(r, List.empty))
  }

}
