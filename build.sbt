import sbt.Keys.crossSbtVersions
import xerial.sbt.Sonatype.sonatypeSettings

lazy val common = Seq(
    organization := "eu.unicredit",
    version := "0.0.12-SNAPSHOT",
    crossSbtVersions := List("0.13.18", "1.2.8"),
    scalaVersion := {
      (sbtBinaryVersion in pluginCrossBuild).value match {
        case "0.13" => "2.10.7"
        case _      => "2.12.8"
      }
    },
    scalacOptions ++= Seq("-feature", "-deprecation", "-language:_"),
    resolvers += Resolver.sonatypeRepo("releases")
  ) ++ sonatypePublish

lazy val lib = project
  .in(file("lib"))
  .settings(common)
  .settings(
    name := """sbt-swagger-codegen-lib""",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.1.9",
      "io.swagger" % "swagger-parser" % "1.0.44"
    )
  )

lazy val plugin = project
  .in(file("plugin"))
  .settings(common)
  .settings(
    name := """sbt-swagger-codegen""",
    sbtPlugin := true
  )
  .dependsOn(lib)

lazy val root = project
  .in(file("."))
  .settings(common)
  .settings(
    skip in publish := true
  )
  .aggregate(lib, plugin)

lazy val sonatypePublish = sonatypeSettings ++ Seq(
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishTo := Some(sonatypeDefaultResolver.value),
    credentials += Credentials(Path.userHome / ".ivy2" / "sonatype.credentials"),
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
      <developer>
        <id>mfirry</id>
        <name>Marco Firrincieli</name>
        <url>https://github.com/mfirry/</url>
      </developer>
    </developers>
  }
  )
