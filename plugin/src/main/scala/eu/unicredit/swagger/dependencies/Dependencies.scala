/* Copyright 2015 UniCredit S.p.A.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package eu.unicredit.swagger.dependencies

import sbt._

object DefaultPlay {
  val version = "2.5.3"
}

object DefaultModelGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq()
}

object DefaultJsonGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq(
    "com.typesafe.play" %% "play-json" % DefaultPlay.version
  )
}

object DefaultServerGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq()
}

object DefaultClientGenerator {
  def dependencies: Seq[sbt.ModuleID] = Seq(
    "com.typesafe.play" %% "play-ws" % DefaultPlay.version
  )
}
