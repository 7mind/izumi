package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.{IzDomainPath, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.ProcessedOp.Exported
import com.github.pshirshov.izumi.idealingua.typer2.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Identified, UnresolvedName}

import scala.collection.mutable

sealed trait TsMember

object TsMember {

  final case class Namespace(prefix: TypePrefix.UserT, types: List[TsMember]) extends TsMember

  final case class UserType(tpe: IzType, sub: List[TsMember]) extends TsMember

}

sealed trait ProcessedOp {
  def member: TsMember
}

object ProcessedOp {

  final case class Exported(member: TsMember) extends ProcessedOp

  final case class Imported(member: TsMember) extends ProcessedOp

}

case class Typespace2(

                       warnings: List[T2Warn],
                       imports: Set[IzTypeId],
                       types: List[TsMember],
                     )

class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) {
  private val failed = mutable.HashSet.empty[UnresolvedName]
  private val failures = mutable.ArrayBuffer.empty[InterpretationFail]
  private val existing = mutable.HashSet.empty[UnresolvedName]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))

  def defined: Set[UnresolvedName] = {
    existing.toSet
  }

  def add(ops: Identified): Unit = {
    ops.defns match {
      case defns if defns.isEmpty =>
        // type requires no ops => builtin
        existing.add(ops.id)
        
      case single :: Nil =>
        val dindex = if (single.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.source)
        }
        val interpreter = new Interpreter(dindex, types.toMap)

        val product = single.defn match {
          case RawTopLevelDefn.TLDBaseType(v) =>
            v match {
              case i: RawTypeDef.Interface =>
                interpreter.makeInterface(i)

              case d: RawTypeDef.DTO =>
                interpreter.makeDto(d)

              case a: RawTypeDef.Alias =>
                interpreter.makeAlias(a)

              case e: RawTypeDef.Enumeration =>
                interpreter.makeEnum(e)

              case i: RawTypeDef.Identifier =>
                interpreter.makeIdentifier(i)
              case a: RawTypeDef.Adt =>
                interpreter.makeAdt(a)
            }

          case c: RawTopLevelDefn.TLDNewtype =>
            interpreter.cloneType(c.v)

          case RawTopLevelDefn.TLDForeignType(v) =>
            interpreter.makeForeign(v)

          case RawTopLevelDefn.TLDDeclared(v) =>
            Left(List(SingleDeclaredType(v)))
        }
        register(ops, product)

      case o =>
        println(s"Unhandled case: ${o.size} defs")
    }
  }

  def fail(ops: Identified, failures: List[InterpretationFail]): Unit = {
    if (ops.depends.exists(failed.contains)) {
      // dependency has failed already, fine to skip
    } else {
      failed.add(ops.id)
      this.failures ++= failures
    }
  }

  def finish(): Either[List[InterpretationFail], Typespace2] = {
    if (failures.nonEmpty) {
      Left(failures.toList)
    } else {
      Right(
        Typespace2(
          List.empty,
          Set.empty,
          Ts2Builder.this.types.values.collect({ case Exported(member) => member }).toList,
        )
      )
    }
  }

  private def register(ops: Identified, product: Either[List[InterpretationFail], IzType]): Unit = {
    (for {
      p <- product
      v <- validate(p)
    } yield {
      v
    }) match {
      case Left(value) =>
        fail(ops, value)

      case Right(value) =>
        val member = TsMember.UserType(value, List.empty)
        types.put(value.id, makeMember(member))
        existing.add(ops.id).discard()
    }
  }

  private def validate(tpe: IzType): Either[List[InterpretationFail], IzType] = {
    // TODO: verify
    Right(tpe)
  }

  private def isOwn(id: IzTypeId): Boolean = {
    id match {
      case IzTypeId.BuiltinType(_) =>
        false
      case IzTypeId.UserType(prefix, _) =>
        prefix == thisPrefix
    }
  }

  private def makeMember(member: TsMember.UserType): ProcessedOp = {
    if (isOwn(member.tpe.id)) {
      ProcessedOp.Exported(member)
    } else {
      ProcessedOp.Imported(member)
    }
  }
}


