package logstage

import com.github.pshirshov.izumi.functional.mono.SyncSafe
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.{Context, CustomContext, Entry, Message}

trait LogInfoF[+F[_]] {
  def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry]
  def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context]
}

object LogInfoF {
  def apply[F[_]: LogInfoF]: LogInfoF[F] = implicitly

  implicit def logInfoFSyncSafeInstance[F[_]: SyncSafe]: LogInfoF[F] = new LogInfoFSyncSafeInstance[F]

  class LogInfoFSyncSafeInstance[F[_]](implicit protected val F: SyncSafe[F]) extends LogInfoF[F] {
    override def createEntry(logLevel: Level, message: Message)(implicit pos: CodePositionMaterializer): F[Entry] = {
      F.syncSafe(Entry.create(logLevel, message)(pos))
    }

    override def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Context] = {
      F.syncSafe(Context.recordContext(logLevel, customContext)(pos))
    }
  }
}
