package zio.interop

/**
  * Spike stand-in, compile-time only: `fundamentals-orphans` names this class in an optional-instance signature and
  * receives zio-interop-cats as an Optional dependency in the normal build. The fixture provides it to `orphans` in
  * Provided scope only, so downstream modules see no zio-interop-cats, exactly like a build without that dependency.
  */
final class CatsIOResourceSyntax[F[_], A] private ()
