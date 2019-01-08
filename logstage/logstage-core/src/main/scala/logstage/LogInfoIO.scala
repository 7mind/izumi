package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.{Context, CustomContext, Entry, Message}

trait LogInfoIO[+F[_]] {
  def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry]
  def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context]
}

object LogInfoIO {
  def apply[F[_]: LogInfoIO]: LogInfoIO[F] = implicitly

  implicit def logInfoFSyncSafeInstance[F[_]: SyncSafe]: LogInfoIO[F] = new LogInfoIOSyncSafeInstance[F]

  class LogInfoIOSyncSafeInstance[F[_]](implicit protected val F: SyncSafe[F]) extends LogInfoIO[F] {
    override def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry] = {
      F.syncSafe(Entry.create(logLevel, message)(pos))
    }

    override def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context] = {
      F.syncSafe(Context.recordContext(logLevel, customContext)(pos))
    }
  }
}
