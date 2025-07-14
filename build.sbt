name := "scala-sdk"
organization := "io.modelcontextprotocol.sdk"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.3.4"

// Dependencies
libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "2.0.7",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2",
  "com.networknt" % "json-schema-validator" % "1.0.84",
  "jakarta.servlet" % "jakarta.servlet-api" % "6.0.0" % Provided,
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)

// Main class for running examples
Compile / mainClass := Some("io.modelcontextprotocol.examples.McpServerExample")
