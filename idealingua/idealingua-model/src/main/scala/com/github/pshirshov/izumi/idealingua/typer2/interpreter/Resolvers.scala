package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.functional.Renderable
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.{RawDeclaredTypeName, RawGenericRef, RawNongenericRef, RawRef}
import com.github.pshirshov.izumi.idealingua.typer2.DomainIndex
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.{IzName, IzNamespace}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeReference.model.{IzTypeArg, IzTypeArgName, IzTypeArgValue}
import com.github.pshirshov.izumi.idealingua.typer2.model.{IzTypeId, IzTypeReference, Rendering}

trait Resolvers {
  def nameToId(id: RawDeclaredTypeName, subpath: Seq[IzNamespace]): IzTypeId

  def nameToTopId(id: RawDeclaredTypeName): IzTypeId

  def resolve(id: RawRef): IzTypeReference

  def refToTopId2(id: IzTypeReference): IzTypeReference

  def genericName(ref: IzTypeReference.Generic): RawDeclaredTypeName
}

class ResolversImpl(context: Interpreter.Args, index: DomainIndex) extends Resolvers {
  def nameToId(id: RawDeclaredTypeName, subpath: Seq[IzNamespace]): IzTypeId = {
    val unresolvedName = index.resolveTopLeveleName(id)
    index.toId(subpath, unresolvedName)
  }

  def resolve(id: RawRef): IzTypeReference = {
    id match {
      case ref@RawNongenericRef(_, _) =>
        context.templateArgs.get(IzTypeArgName(ref.name)) match {
          case Some(value) =>
            value
          case None =>
            IzTypeReference.Scalar(refToTopId(ref))
        }


      case RawGenericRef(pkg, name, args, adhocName) =>
        val id = refToTopId(RawNongenericRef(pkg, name))
        val typeArgs = args.map {
          a =>
            val argValue = resolve(a)
            IzTypeArg(IzTypeArgValue(argValue))
        }
        IzTypeReference.Generic(id, typeArgs, adhocName.map(IzName))

    }
  }

  private def refToTopId(id: RawRef): IzTypeId = {
    val name = index.makeAbstract(id)
    index.toId(Seq.empty, name)
  }

  def nameToTopId(id: RawDeclaredTypeName): IzTypeId = {
    nameToId(id, Seq.empty)
  }

  def genericName(ref: IzTypeReference.Generic): RawDeclaredTypeName = {
    import Rendering._

    val name = ref.adhocName
      .map(n => n.name)
      .getOrElse {
        s"${ref.id.name.name}[${ref.args.map(Renderable[IzTypeArg].render).mkString(",")}]"

      }
    RawDeclaredTypeName(name)
  }

  def refToTopId2(id: IzTypeReference): IzTypeReference = {
    id match {
      case s: IzTypeReference.Scalar =>
        s
      case g: IzTypeReference.Generic =>
        g.id match {
          case IzTypeId.BuiltinType(_) =>
            g
          case _: IzTypeId.UserType =>
            IzTypeReference.Scalar(index.toId(Seq.empty, index.resolveTopLeveleName(genericName(g))))

        }

    }
  }

}
