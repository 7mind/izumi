package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.common.TypeName
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawAdt.Member
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawField, RawNodeMeta, RawStructure, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.Adt
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model.{AdtMemberNested, AdtMemberRef}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzNamespace
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.{BuilderFail, CannotApplyAdtBranchContract, GenericAdtBranchMustBeNamed}
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
      members <- a.alternatives.map(mapMember(id, subpath :+ IzNamespace(id.name.name))(a.contract, _)).biAggregate
      adtMembers = members.map(_.member)
      associatedTypes = members.flatMap(_.additional)
      cdefn = RawTypeDef.Interface(a.id, a.contract.getOrElse(RawStructure.Aux(Seq.empty).structure), a.meta)
      contract <- i2.makeInterface(cdefn, subpath)
    } yield {
      Chain(Adt(adtMembers, i2.meta(a.meta), contract, a.contract), associatedTypes)
    }
  }

  def mapMember(context: IzTypeId, subpath: Seq[IzNamespace])(contract: Option[RawStructure], member: Member): Either[List[BuilderFail], AdtMemberProducts] = {

    contract match {
      case Some(value) =>
        merge(context, subpath, member, value)
      case None =>
        member match {
          case refMember: Member.TypeRef =>
            mapRefMember(context, refMember)

          case nestedMember: Member.NestedDefn =>
            mapNestedMember(subpath, nestedMember)
        }
    }
  }

  private def mapNestedMember(subpath: Seq[IzNamespace], nestedMember: Member.NestedDefn): Either[List[BuilderFail], AdtMemberProducts] = {
    val nested = nestedMember.nested
    val namespace = subpath

    for {
      tpe <- nested match {
        case n: RawTypeDef.Interface =>
          i2.makeInterface(n, namespace).asChain
        case n: RawTypeDef.DTO =>
          i2.makeDto(n, namespace).asChain
        case n: RawTypeDef.Enumeration =>
          i2.makeEnum(n, namespace).asChain
        case n: RawTypeDef.Alias =>
          i2.makeAlias(n, namespace).asChain
        case n: RawTypeDef.Identifier =>
          i2.makeIdentifier(n, namespace).asChain
        case n: RawTypeDef.Adt =>
          makeAdt(n, namespace :+ IzNamespace(nested.id.name))
      }
    } yield {
      AdtMemberProducts(AdtMemberNested(nested.id.name, IzTypeReference.Scalar(tpe.main.id), i2.meta(nested.meta)), tpe.main +: tpe.additional)
    }
  }

  private def merge(context: IzTypeId, subpath: Seq[IzNamespace], member: Member, value: RawStructure): Either[List[BuilderFail], AdtMemberProducts] = {
    member match {
      case ref: Member.TypeRef =>
        typeRefMember(context, subpath, value, ref)

      case n: Member.NestedDefn =>
        n.nested match {
          case templating: RawTypeDef.WithTemplating =>
            templating match {
              case i: RawTypeDef.Interface =>
                mapNestedMember(subpath, n.copy(nested = i.copy(struct = merge(i.struct, value))))

              case d: RawTypeDef.DTO =>
                mapNestedMember(subpath, n.copy(nested = d.copy(struct = merge(d.struct, value))))

              case a: RawTypeDef.Adt =>
                val newContract = a.contract.map(c => merge(value, c)).getOrElse(value)
                mapNestedMember(subpath, n.copy(nested = a.copy(contract = Some(newContract))))
            }

          case a: RawTypeDef.Alias =>
            typeRefMember(context, subpath, value, Member.TypeRef(a.target, Some(a.id.name), a.meta))

          case e: RawTypeDef.Enumeration =>
            Left(List(CannotApplyAdtBranchContract(context, e.id, i2.meta(e.meta))))

          case i: RawTypeDef.Identifier =>
            for {
              nested <- mapNestedMember(subpath, n.copy(nested = i.copy()))
            } yield {
              nested
            }
        }
    }
  }

  private def typeRefMember(context: IzTypeId, subpath: Seq[IzNamespace], value: RawStructure, ref: Member.TypeRef): Either[List[BuilderFail], AdtMemberProducts] = {
    for {
      name <- nameOf(context, ref)
      nameToUse = ref.memberName.getOrElse(name)
      nested <- asDto(value, subpath, ref.typeId, ref.meta, nameToUse)
    } yield {
      nested
    }
  }

  private def asDto(value: RawStructure, subpath: Seq[IzNamespace], typeId: RawRef, meta: RawNodeMeta, nameToUse: TypeName): Either[List[BuilderFail], AdtMemberProducts] = {
    val declaredTypeName = RawDeclaredTypeName(nameToUse)
    val withValue = value.copy(fields = value.fields :+ RawField(typeId, Some("value"), meta))
    val nested = Member.NestedDefn(RawTypeDef.DTO(declaredTypeName, withValue, meta))
    mapNestedMember(subpath, nested)
  }

  private def merge(structure: RawStructure, contract: RawStructure): RawStructure = {
    structure.copy(
      interfaces = contract.interfaces ++ structure.interfaces,
      concepts = contract.concepts ++ structure.concepts,
      removedConcepts = contract.removedConcepts ++ contract.removedConcepts,
      fields = contract.fields ++ contract.fields,
      removedFields = contract.removedFields ++ contract.removedFields,
    )
  }

  private def mapRefMember(context: IzTypeId, refMember: Member.TypeRef): Either[List[BuilderFail], AdtMemberProducts] = {
    val tpe = resolvers.resolve(refMember.typeId)
    for {
      name <- nameOf(context, refMember)
    } yield {
      AdtMemberProducts(AdtMemberRef(name, tpe, i2.meta(refMember.meta)), List.empty)
    }
  }

  private def nameOf(context: IzTypeId, refMember: Member.TypeRef): Either[List[BuilderFail], TypeName] = {
    val tpe = resolvers.resolve(refMember.typeId)
    tpe match {
      case IzTypeReference.Scalar(mid) =>
        Right(refMember.memberName.getOrElse(mid.name.name))
      case ref@IzTypeReference.Generic(_, _, _) =>
        refMember.memberName match {
          case Some(value) =>
            for { // this will trigger instantiation
              _ <- i2.refToTopLevelRef(ref, requiredNow = false)
            } yield {
              value
            }
          case None =>
            Left(List(GenericAdtBranchMustBeNamed(context, refMember.typeId, i2.meta(refMember.meta))))
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
