import SonatypeKeys._

sbtPlugin := true

name := """sbt-swagger-codegen"""

organization := "eu.unicredit"

version := "0.0.5-SNAPSHOT"

crossScalaVersions := Seq("2.10.4")

resolvers += Resolver.sonatypeRepo("releases")

scalacOptions ++= Seq("-target:jvm-1.7",
					  "-feature",
            "-deprecation",
					  "-language:_")

libraryDependencies ++= Seq(
	"com.eed3si9n" %% "treehugger" % "0.4.1",
	"io.swagger" % "swagger-parser" % "1.0.13"
)

sonatypeSettings

publishMavenStyle := true

pomIncludeRepository := { x => false }

credentials += Credentials(Path.userHome / ".ivy2" / "sonatype.credentials")

pomExtra := {
  <url>https://github.com/unicredit/sbt-swagger-codegen</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:github.com/unicredit/sbt-swagger-codegen</connection>
    <developerConnection>scm:git:git@github.com:unicredit/sbt-swagger-codegen</developerConnection>
    <url>github.com/unicredit/sbt-swagger-codegen</url>
  </scm>
  <developers>
    <developer>
      <id>andreaTP</id>
      <name>Andrea Peruffo</name>
      <url>https://github.com/andreaTP/</url>
    </developer>
    <developer>
      <id>fralken</id>
      <name>Francesco Montecuccoli Degli Erri</name>
      <url>https://github.com/fralken/</url>
    </developer>
  </developers>
}