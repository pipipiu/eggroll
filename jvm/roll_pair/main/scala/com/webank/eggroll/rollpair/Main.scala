/*
 * Copyright (c) 2019 - now, Eggroll Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.webank.eggroll.rollpair

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

import _root_.io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import com.webank.eggroll.core.client.NodeManagerClient
import com.webank.eggroll.core.command.{CommandRouter, CommandService}
import com.webank.eggroll.core.constant.{ProcessorStatus, ProcessorTypes, SessionConfKeys, StringConstants}
import com.webank.eggroll.core.meta.{ErEndpoint, ErJob, ErProcessor}
import com.webank.eggroll.core.session.StaticErConf
import com.webank.eggroll.core.util.{Logging, MiscellaneousUtils}
import com.webank.eggroll.rollpair.component.RollPairServicer
import org.apache.commons.lang3.StringUtils


object Main extends Logging {
  def registerRouter():Unit = {
    CommandRouter.register(serviceName = RollPairServicer.rollMapValuesCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.mapValues)

    CommandRouter.register(serviceName = RollPairServicer.rollMapCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.runJob)

    CommandRouter.register(serviceName = RollPairServicer.rollMapPartitionsCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.mapPartitions)

    CommandRouter.register(serviceName = RollPairServicer.rollCollapsePartitionsCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.collapsePartitions)

    CommandRouter.register(serviceName = RollPairServicer.rollFlatMapCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.flatMap)

    CommandRouter.register(serviceName = RollPairServicer.rollGlomCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.glom)

    CommandRouter.register(serviceName = RollPairServicer.rollSampleCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.sample)

    CommandRouter.register(serviceName = RollPairServicer.rollFilterCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.filter)

    CommandRouter.register(serviceName = RollPairServicer.rollSubtractByKeyCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.subtractByKey)

    CommandRouter.register(serviceName = RollPairServicer.rollUnionCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.union)

    CommandRouter.register(serviceName = RollPairServicer.rollReduceCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.reduce)

    CommandRouter.register(serviceName = RollPairServicer.rollJoinCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.runJob)

    CommandRouter.register(serviceName = RollPairServicer.rollRunJobCommand,
      serviceParamTypes = Array(classOf[ErJob]),
      routeToClass = classOf[RollPairServicer],
      routeToMethodName = RollPairServicer.runJob)
  }
  def reportCM(sessionId:String, nm:ErEndpoint, selfPort:Int):Unit = {
    // todo: get port from command line
    // todo: heartbeat service
    val nodeManagerClient = new NodeManagerClient(nm)
    val options = new ConcurrentHashMap[String, String]()
    options.put(SessionConfKeys.CONFKEY_SESSION_ID, sessionId)
    val myself = ErProcessor(
      processorType = ProcessorTypes.ROLL_PAIR_SERVICER,
      commandEndpoint = ErEndpoint("localhost", selfPort),
      dataEndpoint = ErEndpoint("localhost", selfPort),
      options = options,
      status = ProcessorStatus.RUNNING)

    logInfo("ready to heartbeat")
    nodeManagerClient.heartbeat(myself)
  }
  def main(args: Array[String]): Unit = {
    registerRouter()
    val cmd = MiscellaneousUtils.parseArgs(args = args)
    val portString = cmd.getOptionValue('p', "0")
    val sessionId = cmd.getOptionValue('s', "UNKNOWN")
    val nodeManager = cmd.getOptionValue("nm")

    println(nodeManager)
    val managerEndpoint = if (StringUtils.isBlank(nodeManager)) {
      ErEndpoint(host = "localhost", port = 9394)
    } else {
      val splittedManager = nodeManager.trim.split(StringConstants.COLON, 2)
      println(splittedManager)
      println(splittedManager.length)

      val managerHost = if (splittedManager.length == 1) "localhost" else splittedManager(0)
      val managerPort = if (splittedManager.length == 1) splittedManager(0) else splittedManager(1)

      ErEndpoint(host = managerHost, port = managerPort.toInt)
    }

    val rollServer = NettyServerBuilder
      .forAddress(new InetSocketAddress("127.0.0.1", portString.toInt))
      .addService(new CommandService)
      .build
    rollServer.start()
    val port = rollServer.getPort
    StaticErConf.setPort(port)
    StaticErConf.addProperty(SessionConfKeys.CONFKEY_SESSION_ID, sessionId)
    logInfo(s"server started at ${port}")
    // job

    logInfo("server started at port 20000")

    reportCM(sessionId, managerEndpoint, port)


    logInfo("heartbeated")
    rollServer.awaitTermination()

  }
}
