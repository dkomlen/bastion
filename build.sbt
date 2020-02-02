name := "bastion"
version := "0.1.1"
scalaVersion := "2.12.8"

resolvers += Resolver.sonatypeRepo("releases")
assemblyJarName in assembly := "bastion-release.jar"

// Twitter API
libraryDependencies += "com.danielasfregola" %% "twitter4s" % "5.5"

// Logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"

// Config
libraryDependencies += "com.iheart" %% "ficus" % "1.4.3"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings"
)

ThisBuild / githubOwner := "dkomlen"
ThisBuild / organization := "com.dkomlen"
ThisBuild / githubRepository := "bastion"
ThisBuild / githubTokenSource := Some(TokenSource.GitConfig("github.token"))