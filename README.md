`sbt-swagger-codegen` is a plugin for [sbt](www.scala-sbt.org) to generate valid Scala code for a given [Swagger](swagger.io) API specification.

**Install:**

As any other sbt plugin you're just required to delcare it inside your `project\plugins.sbt` like this:

`addSbtPlugin("eu.unicredit" % "sbt-swagger-codegen" % "${VERSION}")`

**How it works:**

By default, the plugin will assume you have put your `yaml` Swagger specification files under `src/main/swagger`.

If so, you can then just run `swaggerCodeGen` and it'll produce your *model's* as case classes under `src/main/scala/swagger/codegen` and [Play Framework](www.playframework.com) [Formats](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators#Format) for them (for json serialization) under `src/main/scala/swagger/codegen/json`.
