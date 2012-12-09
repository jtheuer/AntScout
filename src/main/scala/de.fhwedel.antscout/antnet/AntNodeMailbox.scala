package de.fhwedel.antscout
package antnet

import akka.actor.ActorSystem
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.Config

/**
 * Priorisierte Mailbox für [[de.fhwedel.antscout.antnet.AntNode]].
 *
 * @param settings ActorSystem-Settings
 * @param config Config
 */
class AntNodeMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(
  PriorityGenerator {
    // Aktualisierung der Datenstrukturen wird mit höchster Priorität behandelt.
    case AntNode.UpdateDataStructures(_, _, _) => 0
    // Verarbeitung der Monitoring-Daten wird mit zweit höchster Priorität behandelt.
    case AntNode.ProcessMonitoringData => 1
    case _ => 2
  }
)
