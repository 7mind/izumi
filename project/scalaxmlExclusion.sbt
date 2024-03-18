
// Ignore scala-xml version conflict between scoverage where `coursier` requires scala-xml v2
// and scoverage requires scala-xml v1 on Scala 2.12,
// introduced when updating scoverage to 2.0.7 https://github.com/7mind/idealingua-v1/pull/373/
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
