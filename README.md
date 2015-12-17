# SBT Swagger Code Generator

## Overview

Like official [swagger-codegen](https://github.com/swagger-api/swagger-codegen) this project aims to generate Scala libraries from [Swagger-Specification](https://github.com/swagger-api/swagger-spec) compliant API description.
Moreover you can do it directly within an Sbt project.

## Compatibility

This codegenerator is designed specifically for Swagger Spec Version 2.0. Moreover it relies on [Play! Framework](http://www.playframework.com/) 2.4 for Json marshalling/unmarshalling, Server and Client side code.

## Install

Delcare it inside your `project\plugins.sbt` like this:

`addSbtPlugin("eu.unicredit" % "sbt-swagger-codegen" % "0.0.4-SNAPSHOT")`

If you want to stay on our dev-release:
`sbt publishLocal`

`addSbtPlugin("eu.unicredit" % "sbt-swagger-codegen" % "0.0.6-SNAPSHOT")`

## Quick start

For a *super fast* hands-on the project refer to the related example and check out [sbt-swagger-codegen-examples](https://github.com/unicredit/sbt-swagger-codegen-examples)

## How it works

By default, the plugin will assume you have put your `yaml` Swagger specification files under `src/main/swagger`.

If so, you can then just run `swaggerCodeGen` and it'll produce your *model's* as case classes under `src/main/scala/swagger/codegen` and [Play Framework](www.playframework.com) [Formats](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators#Format) for them (for json serialization) under `src/main/scala/swagger/codegen/json`.


## Commands

All available commands from the plugin

- `swaggerCodeGen`
- `swaggerClean`  -> cleans up already generated code
- `swaggerServerCodeGen` (experimental)   -> generates Play Framework code
- `swaggerClientCodeGen` (experimental)   -> generates client code using `play-ws`

## Tasks

Tasks are provided in order to be chained with other tasks (ex: ```(compile in Compile) <<= (compile in Compile) dependsOn swaggerPlayServerCodeGenTask```

- `swaggerCleanTask`
- `swaggerCodeGenTask`
- `swaggerServerCodeGenTask`
- `swaggerClientCodeGenTask`

## Keys (and defaults)

- `swaggerSourcesDir` 			-> "/src/main/swagger" (path where to search for swagger files)
- `swaggerCodegenPackage`			-> "swagger.codegen" (package name of the generated sources)
- `swaggerModelFileSplitting`		-> "singleFile" (in model generation how to group classes in '.scala' files available options are "oneFilePerSource" "oneFilePerModel")
- `swaggerGeneratePlayJsonRW`		-> true (if you want to generate json Format for your model case classes)
- `swaggerCodeProvidedPackage`	-> "eu.unicredit" (where you will provide business logic server method implementation)
- `swaggerModelCodeTargetDir`	-> "/src/main/scala" (path where to put generated model files)
- `swaggerClientCodeTargetDir`	-> "/src/main/scala" (path where to put generated client code files)
- `swaggerPlayServerRoutesFile`	-> "/src/main/resources/routes" (routes file to be generated)
- `swaggerGenerateControllers`	-> true (to be disabled if you want to provide fully costom controllers with all the boilerplate)

Moreover you can extend this plugin by providing alternative implementations of the generators via:

- `swaggerModelCodeGenClass` -> new eu.unicredit.swagger.generators.DefaultModelGenerator() (the class used to generate the model classes)
- `swaggerJsonCodeGenClass` -> new eu.unicredit.swagger.generators.DefaultJsonGenerator() (the class used to generate the json marshaller/unmarshaller)
- `swaggerServerCodeGenClass` -> new eu.unicredit.swagger.generators.DefaultServerGenerator() (the class used to generate the Server classes)
- `swaggerModelClientGenClass` -> new eu.unicredit.swagger.generators.DefaultClientGenerator() (the class used to generate the client classes)

## Dependencies

- scala version 2.11.X
- play-sbt-plugin 2.4.4
- play-json 2.4.4
- joda-time 2.7
- joda-convert 1.7
- play-ws 2.4.4 (only if you use client)

### Limitations

At the moment the project is developed to fullfill some internal projects needs, do not expect this to cover all the corner cases of the Swagger-Spec(i.e. some primitive types in body req or resp).
Model objects must have less than 22 parameters and could fail with circular referencies(due to the Play Json macro limitaion).

## The road ahead

We are actively working with and on this project, trying to overcome any limitation arise and any Swagger-spec property we need.
PR are really welcome and please open an Issue if you find something not working.

## Authors:

* Andrea Peruffo: <https://github.com/andreaTP>
* Francesco Montecuccoli Degli Erri <https://github.com/fralken>
* Marco Firrincieli: <https://github.com/mfirry>

**** This is a work in progress and are not done with it! ****
