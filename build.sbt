name := "enrichmentool"

version := "1.0"

scalaVersion := "2.12.1"

libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.18"
libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "10.1.5"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5"
libraryDependencies += "net.debasishg"     %% "redisclient" % "3.8"
libraryDependencies += "io.spray"          %% "spray-json" % "1.3.5"
libraryDependencies += "ch.qos.logback"    %  "logback-classic" % "1.2.3"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
