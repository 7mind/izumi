package izumi.distage.reflection.macros.universe.basicuniverse.exceptions

class BadIdAnnotationException(typeOfIdAnnotation: String, value: Any)
  extends RuntimeException(s"Wrong annotation value, only constants are supported. Got: @$typeOfIdAnnotation($value)")
