/*
 * Copyright 2012 Alexander Bertram
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

package de.fhwedel.antscout

import antnet._
import osm.OsmMap
import routing.RoutingService
import akka.actor.{Props, Actor, FSM}

/**
 * Initialisiert die Anwendung mit Hilfe eines Zustands-Automaten.
 */
class AntScout extends Actor with FSM[AntScoutState, Unit] {

  import AntScout._

  // AntNodeSupervisor erzeugen
  context.actorOf(Props[AntNodeSupervisor].withDispatcher("ant-node-supervisor-dispatcher"),
    AntNodeSupervisor.ActorName)
  // Routing-Service erzeugen
  context.actorOf(Props[RoutingService], RoutingService.ActorName)

  // Start-Zustand
  startWith(Uninitialized, Unit)

  // Uninitialisiert
  when(Uninitialized) {
    // Initialisierung anstoßen
    case Event(Initialize, _) =>
      // OsmMap initialisieren
      OsmMap(Settings.Map)
      // RoutingService-Initialisierung anstoßen
      context.actorFor(RoutingService.ActorName) ! RoutingService.Initialize
      // Zustand wechseln
      goto(InitializingRoutingService)
  }

  // RoutingService-Initialisierung
  when(InitializingRoutingService) {
    // RoutingService initialisiert
    case Event(RoutingServiceInitialized, _) =>
      // Ant-Weg-Daten berechnen
      val antWayData = AntMap.prepare
      // Ant-Knoten berechnen
      AntMap.computeNodes(antWayData)
      // AntNodeSupervisor-Initialisierung anstoßen
      context.actorFor(AntNodeSupervisor.ActorName) ! AntNodeSupervisor.Initialize(antWayData)
      // Zustand wechseln
      goto(InitializingAntNodeSupervisor)
  }

  // AntNodeSupervisor-Initialisierung
  when(InitializingAntNodeSupervisor) {
    // AntNodeSupervisor initialisiert
    case Event(AntNodeSupervisor.Initialized(antWayData), _) =>
      // Ant-Wege berechnen
      AntMap.computeAntWays(antWayData)
      // Ein- und ausgehende Wege berechnen
      AntMap.computeIncomingAndOutgoingWays()
      // Quellen und Ziele berechnen
      AntMap.computeSourcesAndDestinations()
      // Zusicherung, dass Knoten nicht leer sind
      assert(AntMap.nodes.size > 0, AntMap.nodes.size)
      // Initialisierung der Ant-Knoten anstoßen
      context.actorFor(AntNodeSupervisor.ActorName) ! AntNodeSupervisor.InitializeNodes
      // In diesem Zustand bleiben
      stay()
  }

  // Zustands-Automaten initialisieren
  initialize
}

/**
 * AntScout-Factory.
 */
object AntScout {

  /**
   * Aktor-Name
   */
  val ActorName = "antScout"

  /**
   * Uninitialsiert.
   */
  case object Uninitialized extends AntScoutState

  /**
   * Initialiserung anstoßen.
   */
  case object Initialize extends AntScoutMessage

  /**
   * AntNodeSupervisor-Initialisierung.
   */
  case object InitializingAntNodeSupervisor extends AntScoutState

  /**
   * RoutingService-Initialisierung.
   */
  case object InitializingRoutingService extends AntScoutState

  /**
   * RoutingService initialisiert.
   */
  case object RoutingServiceInitialized extends AntScoutMessage

  // AntScout-Aktor erzeugen
  system.actorOf(Props[AntScout], AntScout.ActorName)

  /**
   * Initialisiert AntScout.
   */
  def init() {
    system.actorFor(Iterable("user", ActorName)) ! Initialize
  }

  /**
   * Fährt AntScout herunter.
   */
  def shutDown() {
    system.shutdown()
  }
}

/**
 * Gemeinsame Basis für die AntScout-Nachrichten.
 */
sealed trait AntScoutMessage

/**
 * Gemeinsame Basis für die AntScout-Zustände.
 */
sealed trait AntScoutState
