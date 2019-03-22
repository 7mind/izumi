package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec._


// raw annotation
final case class RestAnnotation(path: String, method: HttpMethod)

// final annotation
final case class RestSpec(method: HttpMethod, extractor: ExtractorSpec, body: BodySpec)

object RestSpec {
  sealed trait OnWireType
  final case class OnWireScalar(id: IzTypeReference.Scalar) extends OnWireType // will always be ref to builtin scalar
  final case class OnWireOption(ref: IzTypeReference.Generic) extends OnWireType // will always be ref to opt

  sealed trait QueryParameterSpec {
    def onWire: OnWireType
  }

  object QueryParameterSpec {
    final case class List(parameter: BasicField, path: BasicField, onWire: OnWireType) extends QueryParameterSpec
    final case class Scalar(parameter: BasicField, path: Seq[BasicField], onWire: OnWireType, optional: Boolean) extends QueryParameterSpec
  }

  sealed trait PathSegment

  object PathSegment {



    final case class Word(value: String) extends PathSegment
    final case class Parameter(parameter: BasicField, path: Seq[BasicField], onWire: OnWireType) extends PathSegment
  }

  final case class QueryParameterName(value: String) extends AnyVal
  final case class ExtractorSpec(queryParameters: Map[QueryParameterName, QueryParameterSpec], pathSpec: Seq[PathSegment])
  final case class BodySpec(fields: Seq[BasicField])

  sealed trait HttpMethod {
    def name: String
  }

  object HttpMethod {
    final val all = List(Get, Post, Put, Delete, Patch)
      .map {
        m =>
          m.name.toLowerCase -> m
      }
      .toMap

    final case object Get extends HttpMethod {
      override def name: String = "Get"
    }

    final case object Post extends HttpMethod {
      override def name: String = "Post"
    }

    final case object Put extends HttpMethod {
      override def name: String = "Put"
    }

    final case object Delete extends HttpMethod {
      override def name: String = "Delete"
    }

    final case object Patch extends HttpMethod {
      override def name: String = "Patch"
    }

    // final case object Head extends HttpMethod
    //  final case object Connect extends HttpMethod
    //  final case object Options extends HttpMethod
    //  final case object Trace extends HttpMethod
  }
}



