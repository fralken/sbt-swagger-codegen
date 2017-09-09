lazy val common = Seq(
    organization := "eu.unicredit",
    version := "0.0.11-SNAPSHOT",
    crossScalaVersions := Seq("2.10.4"),
    scalacOptions ++= Seq("-target:jvm-1.7", "-feature", "-deprecation", "-language:_"),
    resolvers += Resolver.sonatypeRepo("releases")
  ) ++ sonatypePublish

lazy val lib = project
  .in(file("lib"))
  .settings(common)
  .settings(
    name := """sbt-swagger-codegen-lib""",
    libraryDependencies ++= Seq(
      "com.eed3si9n" %% "treehugger" % "0.4.3",
      "io.swagger" % "swagger-parser" % "1.0.27"
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
  .settings(
    publish := {}
  )
  .aggregate(lib, plugin)

publishArtifact := false

lazy val sonatypePublish = sonatypeSettings ++ Seq(
    publishMavenStyle := true,
    pomIncludeRepository := { _ =>
    false
  },
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
