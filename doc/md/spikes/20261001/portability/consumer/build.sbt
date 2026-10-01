scalaVersion := sys.props("spike.consumerScala")
Compile / unmanagedJars ++= (file(sys.props("spike.localArtifactDir")) * "*.jar").classpath
Compile / mainClass := Some("Consumer")
Compile / unmanagedSources / excludeFilter := new SimpleFileFilter(f => f.getName == (if (sys.props.contains("spike.compilerProbe")) "Consumer.scala" else "CompilerConsumer.scala"))
Compile / mainClass := Some(if (sys.props.contains("spike.compilerProbe")) "CompilerConsumer" else "Consumer")
