name := "bitflux"

version := "0.1"

scalaVersion := "2.11.2"

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "1.0.0"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.6"

libraryDependencies += "joda-time" % "joda-time" % "2.4"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"
