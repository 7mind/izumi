package com.github.pshirshov.izumi.fundamentals.platform.exceptions

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

class IzThrowable(t: Throwable, acceptedPackages: Set[String]) {

  import IzThrowable._

  def forPackages(acceptedPackages: Set[String]): IzThrowable = {
    new IzThrowable(t, this.acceptedPackages ++ acceptedPackages)
  }

  def format: String = {
    val messages = t.causes.map {
      currentThrowable =>
        val origin = t.stackTop match {
          case Some(frame) =>
            s"${frame.getFileName}:${frame.getLineNumber}"
          case _ =>
            "?"
        }
        s"${t.getMessage}@${t.getClass.getSimpleName} $origin"
    }

    messages.mkString(", due ")
  }

  def allMessages: Seq[String] = {
    t.causes.map(_.getMessage)
  }

  def allClasses: Seq[String] = {
    t.causes.map(_.getClass.getCanonicalName)
  }

  def causes: Seq[Throwable] = {
    val ret = new ArrayBuffer[Throwable]()
    var currentThrowable = t
    while (currentThrowable != null) {
      ret.append(currentThrowable)
      currentThrowable = currentThrowable.getCause
    }
    ret
  }

  def stackTop: Option[StackTraceElement] = {
    t.getStackTrace.find {
      frame =>
        !frame.isNativeMethod && acceptedPackages.exists(frame.getClassName.startsWith)
    }
  }
}

object IzThrowable {
  implicit def toRich(throwable: Throwable): IzThrowable = new IzThrowable(throwable, Set.empty)
}

