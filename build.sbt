name := "bitflux"

version := "0.1"

scalaVersion := "2.11.7"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
 
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.6"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.14"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"
