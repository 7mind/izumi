package com.github.pshirshov.izumi.idealingua.typer2.model

import com.github.pshirshov.izumi.idealingua.typer2.model.IzType.model._
import com.github.pshirshov.izumi.idealingua.typer2.model.RestSpec._


// raw annotation
final case class RestAnnotation(path: String, method: HttpMethod)

final case class RestSpec(method: HttpMethod, extractor: ExtractorSpec, body: BodySpec)

object RestSpec {
  sealed trait QueryParameterSpec {
    def path: Seq[BasicField]
    def onWire: IzTypeId.BuiltinTypeId
  }

  object QueryParameterSpec {
    final case class List(path: Seq[BasicField], onWire: IzTypeId.BuiltinTypeId) extends QueryParameterSpec
    final case class Scalar(path: Seq[BasicField], onWire: IzTypeId.BuiltinTypeId) extends QueryParameterSpec
  }

  sealed trait PathSegment

  object PathSegment {
    final case class Word(value: String) extends PathSegment
    final case class Parameter(path: Seq[BasicField], onWire: IzTypeId.BuiltinTypeId) extends PathSegment
  }

  final case class QueryParameterName(value: String) extends AnyVal
  final case class ExtractorSpec(queryParameters: Map[QueryParameterName, QueryParameterSpec], pathSpec: Seq[PathSegment])
  final case class BodySpec(fields: Seq[BasicField])

  sealed trait HttpMethod {
    def name: String
  }

  object HttpMethod {

    final case object Get extends HttpMethod {
      override def name: String = "get"
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



