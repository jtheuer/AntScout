package de.fhwedel.antscout

import antnet._
import net.liftweb.common.Logger
import osm.OsmMap
import routing.RoutingService
import net.liftweb.util.Props
import akka.actor.{ActorSystem, FSM, Actor}
import akka.actor
import com.typesafe.config.ConfigFactory

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 18.11.11
 * Time: 09:20
 */

sealed trait AntScoutMessage

class AntScout extends Actor with FSM[AntScoutMessage, Unit] with Logger {

  import AntScout._

  val antLauncher = context.actorOf(actor.Props[AntLauncher], "antLauncher")

  startWith(Uninitialized, Unit)

  when(Uninitialized) {
    case Event(Initialize, _) =>
      val map = Props.get("map")
      assert(map isDefined)
      OsmMap(map get)
      AntMap()
      pheromonMatrixSupervisor ! PheromonMatrixSupervisor.Initialize(AntMap.sources, AntMap.destinations)
      goto(InitializingPheromonMatrixSupervisor)
  }

  when(InitializingPheromonMatrixSupervisor) {
    case Event(PheromonMatrixSupervisorInitialized, _) =>
      val varsigma = Props.get("varsigma").map(_.toDouble) openOr TrafficModel.DefaultVarsigma
      AntScout.trafficModelSupervisor ! TrafficModelSupervisor.Initialize(AntMap.sources, AntMap.destinations, varsigma)
    goto(InitializingTrafficModelSupervisor)
  }

  when(InitializingTrafficModelSupervisor) {
    case Event(TrafficModelSupervisorInitialized, _) =>
      AntScout.routingService ! RoutingService.Initialize
    goto(InitializingRoutingService)
  }

  when(InitializingRoutingService) {
    case Event(RoutingServiceInitialized, _) =>
      antLauncher ! AntLauncher.Start
    stay()
  }

  initialize
}

object AntScout {

  case object Uninitialized extends AntScoutMessage
  case object Initialize extends AntScoutMessage
  case object InitializingPheromonMatrixSupervisor extends AntScoutMessage
  case object PheromonMatrixSupervisorInitialized extends AntScoutMessage
  case object InitializingTrafficModelSupervisor extends AntScoutMessage
  case object TrafficModelSupervisorInitialized extends AntScoutMessage
  case object InitializingRoutingService extends AntScoutMessage
  case object RoutingServiceInitialized extends AntScoutMessage

  val config = ConfigFactory.load
  val system = ActorSystem("AntScout", config)
  val instance = system.actorOf(actor.Props[AntScout], "antScout")
  val pheromonMatrixSupervisor = system.actorOf(actor.Props[PheromonMatrixSupervisor], "pheromonMatrixSupervisor")
  val routingService = system.actorOf(actor.Props[RoutingService], "routingService")
  val trafficModelSupervisor = system.actorOf(actor.Props[TrafficModelSupervisor], "trafficModelSupervisor")

  def init() {
    instance ! Initialize
  }

  def shutDown() {
    system.shutdown()
  }
}
