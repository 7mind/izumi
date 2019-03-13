package com.github.pshirshov.izumi.idealingua.typer2.interpreter

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawMethod.Output
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.typeid.RawDeclaredTypeName
import com.github.pshirshov.izumi.idealingua.typer2.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.{Buzzer, Service}
import com.github.pshirshov.izumi.idealingua.typer2.model.IzTypeId.model.IzNamespace
import com.github.pshirshov.izumi.idealingua.typer2.model.T2Fail.FeatureUnsupported
import com.github.pshirshov.izumi.idealingua.typer2.results._


class InterfaceSupport(
                        i2: TypedefSupport,
                        adtSupport: AdtSupport,
                        resolvers: Resolvers,
                      ) {
  def makeStream(s: RawTypeDef.RawStreams): TList = {
    val id = resolvers.nameToId(s.id, Seq.empty)
    Left(List(FeatureUnsupported(id, "Streams are not supported yet", i2.meta(s.meta))))
  }


  import InterfaceSupport._

  def makeService(a: RawTypeDef.RawService): TList = {
    val ns = Seq(IzNamespace(a.id.name))
    for {
      methods <- a.methods.map(makeMethod(ns)).biAggregate
    } yield {
      List(Service(resolvers.nameToId(a.id, Seq.empty), methods.map(_.product), i2.meta(a.meta))) ++ methods.flatMap(_.types)
    }
  }

  def makeBuzzer(a: RawTypeDef.RawBuzzer): TList = {
    val ns = Seq(IzNamespace(a.id.name))
    for {
      methods <- a.events.map(makeMethod(ns)).biAggregate
    } yield {
      List(Buzzer(resolvers.nameToId(a.id, Seq.empty), methods.map(_.product), i2.meta(a.meta))) ++ methods.flatMap(_.types)
    }
  }

  private def makeMethod(ns: Seq[IzNamespace])(m: RawMethod): Result[Product[IzMethod]] = {
    m match {
      case RawMethod.RPCMethod(name, signature, meta) =>
        for {
          input <- mapInput(ns, meta, name, signature.input)
          output <- mapOutput(ns, meta, name, signature.output)
        } yield {
          Product(
            IzMethod(name, input.product, output.product, i2.meta(meta)),
            input.types ++ output.types,
          )
        }
    }
  }

  private def mapInput(ns: Seq[IzNamespace], mmeta: RawNodeMeta, methodName: String, s: RawSimpleStructure): Result[Product[IzInput]] = {
    for {
      dto <- i2.makeDto(RawTypeDef.DTO(RawDeclaredTypeName(methodName.capitalize + "Input"), toFullStruct(s), mmeta), ns)
    } yield {
      Product(IzInput.Singular(IzTypeReference.Scalar(dto.id)), List(dto))
    }
  }

  private def mapOutput(ns: Seq[IzNamespace], mmeta: RawNodeMeta, methodName: String, s: RawMethod.Output): Result[Product[IzOutput]] = {
    s match {
      case output: Output.NonAlternativeOutput =>
        makeNa(ns, mmeta, methodName, output)
      case Output.Alternative(success, failure) =>
        for {
          s <- makeNa(ns, mmeta, methodName, success)
          f <- makeNa(ns, mmeta, methodName, failure)
        } yield {
          Product(IzOutput.Alternative(s.product, f.product), s.types ++ f.types)
        }
    }

  }

  private def makeNa(ns: Seq[IzNamespace], mmeta: RawNodeMeta, methodName: String, output: Output.NonAlternativeOutput): Result[Product[IzOutput.Basic]] = {
    val name = RawDeclaredTypeName(methodName.capitalize + "Output")
    output match {
      case Output.Struct(input) =>
        for {
          dto <- i2.makeDto(RawTypeDef.DTO(name, toFullStruct(input), mmeta), ns)
        } yield {
          Product(IzOutput.Singular(IzTypeReference.Scalar(dto.id)), List(dto))
        }
      case Output.Algebraic(alternatives, contract) =>
        for {
          adt <- adtSupport.makeAdt(RawTypeDef.Adt(name, contract, alternatives, mmeta), ns)
        } yield {
          Product(IzOutput.Singular(IzTypeReference.Scalar(adt.main.id)), adt.flatten)
        }
      case Output.Singular(typeId) =>
        for {
          ref <- i2.refToTopLevelRef(resolvers.resolve(typeId), requiredNow = false)
        } yield {
          Product(IzOutput.Singular(ref), List.empty)
        }
      case Output.Void() =>
        for {
          dto <- i2.makeDto(RawTypeDef.DTO(name, toFullStruct(RawSimpleStructure(List.empty, List.empty)), mmeta), ns)
        } yield {
          Product(IzOutput.Singular(IzTypeReference.Scalar(dto.id)), List(dto))
        }
    }
  }

  private def toFullStruct(s: RawSimpleStructure): RawStructure = {
    RawStructure(List.empty, s.concepts, List.empty, s.fields, List.empty)
  }

}

object InterfaceSupport {

  case class Product[+T](product: T, types: List[IzType])

}
