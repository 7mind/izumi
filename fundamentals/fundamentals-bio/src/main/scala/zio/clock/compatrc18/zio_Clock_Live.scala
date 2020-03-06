package zio.clock.compatrc18

import java.time.{Instant, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import zio.clock.Clock
import zio.duration.Duration
import zio.{Has, IO, UIO, ZIO}

// workaround for missing static Clock.Live
object zio_Clock_Live extends Clock.Service {
  def currentTime(unit: TimeUnit): UIO[Long] = {
    IO.effectTotal(System.currentTimeMillis).map(l => unit.convert(l, TimeUnit.MILLISECONDS))
  }
  val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime)

  def sleep(duration: Duration): UIO[Unit] = {
    UIO.effectAsyncInterrupt {
      cb =>
        val canceler = zio.clock.Clock.globalScheduler.schedule(() => cb(UIO.unit), duration)
        Left(UIO.effectTotal(canceler()))
    }
  }
  def currentDateTime: ZIO[Any, Nothing, OffsetDateTime] = {
    for {
      millis <- currentTime(TimeUnit.MILLISECONDS)
      zone <- ZIO.effectTotal(ZoneId.systemDefault)
    } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
  }

  val live: Has[Clock.Service] = Has(this)
}
