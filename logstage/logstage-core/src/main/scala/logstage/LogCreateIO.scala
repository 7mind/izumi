package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.{Context, CustomContext, Entry, Message}

trait LogCreateIO[F[_]] {
  def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry]
  def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context]
}

object LogCreateIO {
  def apply[F[_]: LogCreateIO]: LogCreateIO[F] = implicitly

  /**
    * Please enable `-Xsource:2.13` compiler option
    * If you're having trouble calling this method
    * with a single parameter `F`, e.g. `cats.effect.IO`
    *
    * @see bug https://github.com/scala/bug/issues/11435 for details
    *     [FIXED in 2.13 and in 2.12 with `-Xsource:2.13` flag]
    */
  implicit def logCreateIOSyncSafeInstance[F[_]: SyncSafe]: LogCreateIO[F] = new LogCreateIOSyncSafeInstance[F]

  class LogCreateIOSyncSafeInstance[F[_]](implicit protected val F: SyncSafe[F]) extends LogCreateIO[F] {
    override def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry] = {
      F.syncSafe(Entry.create(logLevel, message)(pos))
    }

    override def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context] = {
      F.syncSafe(Context.recordContext(logLevel, customContext)(pos))
    }
  }
}
