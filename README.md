# SBT Swagger Code Generator

## Overview

Like the official [swagger-codegen](https://github.com/swagger-api/swagger-codegen) this project aims to generate Scala source code from [Swagger Specification](https://github.com/swagger-api/swagger-spec) compliant API descriptions.
Moreover, you can do it directly within an `sbt` project.

## Compatibility

This code generator is designed specifically for Swagger Spec Version 2.0. Moreover, it relies on [Play! Framework](http://www.playframework.com/) 2.5 for Json marshalling/unmarshalling, server- and client-side code.

## Install

Enable it inside your `project\plugins.sbt` like this:

`addSbtPlugin("eu.unicredit" % "sbt-swagger-codegen" % "0.0.7-SNAPSHOT")`

## Quick start

For a *super fast* hands-on tutorial refer to the related examples and check out [sbt-swagger-codegen-examples](https://github.com/unicredit/sbt-swagger-codegen-examples).

## How it works

By default, the plugin will assume that you have put your `yaml` Swagger specification files under `src/main/swagger`.

If so, then you can just run `swaggerCodeGen` and it will generate your *model's* as case classes under `src/main/scala/swagger/codegen` and [Play Framework](www.playframework.com) [Formats](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators#Format) for them (for json serialization) under `src/main/scala/swagger/codegen/json`.

## Tasks

All available tasks from the plugin

- `swaggerCodeGen`
- `swaggerClean` -> cleans up already generated code
- `swaggerServerCodeGen` -> generates Play Framework code
- `swaggerClientCodeGen` -> generates client code using `play-ws`

Tasks can be chained with other tasks (ex: ```(compile in Compile) <<= (compile in Compile) dependsOn swaggerServerCodeGen```.

## Keys (and defaults)

- `swaggerSourcesDir` -> "/src/main/swagger" (path where to search for swagger files)
- `swaggerCodeGenPackage` -> "swagger.codegen" (package name of the generated sources)
- `swaggerModelFileSplitting` -> "singleFile" (in model generation how to group classes in '.scala' files available options are "oneFilePerSource" "oneFilePerModel")
- `swaggerGeneratePlayJsonRW` -> true (if you want to generate json Format for your model case classes)
- `swaggerCodeProvidedPackage` -> "eu.unicredit" (where you will provide business logic server method implementation)
- `swaggerModelCodeTargetDir` -> "/src/main/scala" (path where to put generated model files)
- `swaggerClientCodeTargetDir` -> "/src/main/scala" (path where to put generated client code files)
- `swaggerServerRoutesFile` -> "/src/main/resources/routes" (routes file to be generated)
- `swaggerGenerateControllers` -> true (to be disabled if you want to provide fully costom controllers with all the boilerplate)

Moreover, you can extend this plugin by providing alternative implementations of the generators via:

- `swaggerModelCodeGenClass` -> new eu.unicredit.swagger.generators.DefaultModelGenerator() (the class used to generate the model classes)
- `swaggerJsonCodeGenClass` -> new eu.unicredit.swagger.generators.DefaultJsonGenerator() (the class used to generate the json marshaller/unmarshaller)
- `swaggerServerCodeGenClass` -> new eu.unicredit.swagger.generators.DefaultServerGenerator() (the class used to generate the Server classes)
- `swaggerModelClientGenClass` -> new eu.unicredit.swagger.generators.DefaultClientGenerator() (the class used to generate the client classes)

## Dependencies

- scala version 2.11.X
- play-sbt-plugin 2.5.3
- play-json 2.5.3
- play-ws 2.5.3 (only if you use client)

### Limitations

At the moment the project is developed to fullfill some internal projects needs, so do not expect it to cover all the corner cases of the Swagger Spec (i.e. some primitive types in body req or resp).
Model objects must have less than 22 parameters and could fail with circular referencies(due to the Play Json macro limitaion).

## The road ahead

We are actively working with and on this project, trying to overcome any arising limitations and support all Swagger-spec properties we need.
PRs are really welcome and please open an Issue if you find that something is not working.

## Authors:

* Andrea Peruffo: <https://github.com/andreaTP>
* Francesco Montecuccoli Degli Erri <https://github.com/fralken>
* Marco Firrincieli: <https://github.com/mfirry>

*** This is a work in progress and we are not done with it! ***
