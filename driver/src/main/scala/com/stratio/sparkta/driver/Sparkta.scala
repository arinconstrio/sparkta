/**
 * Copyright (C) 2015 Stratio (http://stratio.com)
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

package com.stratio.sparkta.driver

import java.io.File
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.slf4j.SLF4JLogging
import akka.io.IO
import com.stratio.sparkta.driver.actor.{PolicyControllerActor, SupervisorActor}
import com.stratio.sparkta.driver.factory.JarListFactory._
import com.stratio.sparkta.driver.service.StreamingContextService
import com.typesafe.config.ConfigFactory
import spray.can.Http

/**
 * Entry point of the application.
 */
object Sparkta extends App with SLF4JLogging {

  var sparktaHome: Option[String] = Option(System.getenv("SPARKTA_HOME")).orElse({
    sparktaHome = Option(System.getProperty("user.dir", "./"))
    log.warn("SPARKTA_HOME environment variable is not set, defaulting to {}", sparktaHome)
    sparktaHome
  })

  val jarsPath = new File(sparktaHome.get, "plugins")
  log.info("Loading jars from " + jarsPath.getAbsolutePath)

  val jdkPath = new File(sparktaHome.get, "sdk");
  val aggregatorPath = new File(sparktaHome.get, "aggregator");

  val jars = findJarsByPath(jarsPath) ++ findJarsByPath(jdkPath) ++ findJarsByPath(aggregatorPath)

  log.info("Loading configuration...")
  val sparktaConfig = ConfigFactory.load().getConfig("sparkta")

  log.info("Starting Actor System...")
  implicit val system = ActorSystem("sparkta")

  val streamingContextService = new StreamingContextService(sparktaConfig, jars)

  log.info("Starting Supervisor Actor...")
  val supervisor: ActorRef = system.actorOf(Props(new SupervisorActor(streamingContextService)), "supervisor")

  log.info("Starting REST api...")
  val controller = system.actorOf(Props(new PolicyControllerActor(supervisor)), "workflowController")

  val apiConfig = sparktaConfig.getConfig("api")

  IO(Http) ! Http.Bind(controller, interface = apiConfig.getString("host"), port = apiConfig.getInt("port"))

  log.info("System UP!")
}
