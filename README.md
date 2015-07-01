`sbt-swagger-codegen` is a plugin for [sbt](www.scala-sbt.org) to generate valid Scala code for a given [Swagger](swagger.io) API specification.

**Install**

As any other sbt plugin you're just required to delcare it inside your `project\plugins.sbt` like this:

`addSbtPlugin("eu.unicredit" % "sbt-swagger-codegen" % "${VERSION}")`

**How it works**

By default, the plugin will assume you have put your `yaml` Swagger specification files under `src/main/swagger`.

If so, you can then just run `swaggerCodeGen` and it'll produce your *model's* as case classes under `src/main/scala/swagger/codegen` and [Play Framework](www.playframework.com) [Formats](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators#Format) for them (for json serialization) under `src/main/scala/swagger/codegen/json`.


**Commands**

All available commands from the plugin 

- swaggerClean
- swaggerCodeGen
- swaggerPlayServerCodeGen
- swaggerPlayClientCodeGen
 
**Tasks**

Tasks are provided in order to be chained with other tasks (ex: ```(compile in Compile) <<= (compile in Compile) dependsOn swaggerPlayServerCodeGenTask```

- swaggerCleanTask
- swaggerCodeGenTask
- swaggerPlayServerCodeGenTask
- swaggerPlayClientCodeGenTask


**Keys (and defaults)**

- swaggerSourcesDir 			-> "/src/main/swagger" (path where to search for swagger files)
- swaggerCodegenPackage			-> "swagger.codegen" (package name of the generated sources)
- swaggerModelFileSplitting		-> "singleFile" (in model generation how to group classes in '.scala' files available options are "oneFilePerSource" "oneFilePerModel")
- swaggerGeneratePlayJsonRW		-> true (if you want to generate json Format for your model case classes)
- swaggerCodeProvidedPackage	-> "eu.unicredit" (where you will provide business logic server method implementation)
- swaggerServerAsync			-> false (if the server business logic will work retrieving Future or actual values)

**Dependencies**

**Limitations**

**The road ahead**