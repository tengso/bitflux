name := "bitflux"

version := "0.1"

scalaVersion := "2.11.2"

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.6"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"
