/*
 * Spike stand-in for zio-interop-tracer on Scala Native, which publishes no Native artifact up to 23.1.0.13.
 * Mirrors the upstream JS implementation (zio/interop-cats v23.1.0.5,
 * zio-interop-tracer/js/src/main/scala/zio/internal/stacktracer/InteropTracer.scala, Apache License 2.0,
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors).
 * Replace with the upstream Native artifact once it is published.
 */
package zio.internal.stacktracer

import scala.annotation.nowarn

object InteropTracer {
  @nowarn("cat=unused")
  final def newTrace(f: Any): Trace = "noop".asInstanceOf[Trace]

  private type Trace = Tracer.instance.Type with Tracer.Traced
}
