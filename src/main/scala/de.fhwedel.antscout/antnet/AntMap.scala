package de.fhwedel.antscout
package antnet

import net.liftweb.common.Logger
import osm.{OsmOneWay, OsmWay, OsmNode, OsmMap}
import net.liftweb.util.{Props, TimeHelpers}
import annotation.tailrec
import collection.immutable.{Set, Map}
import collection.mutable
import collection.mutable.ListBuffer
import map.Node

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 02.12.11
 * Time: 12:07
 */

object AntMap extends Logger {

  val DefaultRelevantHighways = ""

  /**
   * Ziele
   */
  private var _destinations: Set[Node] = _
  /**
   * Eingehende Wege pro Knoten
   */
  private var _incomingWays: Map[Node, Set[AntWay]] = _
  /**
   * Knoten
   */
  private val _nodes = mutable.Set[Node]()
  /**
   * Ausgehende Wege pro Knoten
   */
  private var _outgoingWays: Map[Node, Set[AntWay]] = _
  /**
   * Ziele
   */
  private var _sources: Set[Node] = _
  /**
   * Wege
   */
  private var _ways: Set[AntWay] = _

  def apply() {
    info("%d ant nodes".format(_nodes.size))
//    info("Nodes without incoming ways: %s".format(_nodes.filter(n => !incomingWays.contains(n)).map(n => "\nhttp://www.openstreetmap.org/browse/node/%s".format(n.id))))
//    info("Nodes without outgoing ways: %s".format(_nodes.filter(n => !outgoingWays.contains(n)).map(n => "\nhttp://www.openstreetmap.org/browse/node/%s".format(n.id))))
  }

  /**
   * Berechnet Ant-Wege anhand einer Abbildung von Knoten auf die adjazenten Wege.
   *
   * @param nodeWaysMapping Abbildung von Knoten auf die adjazenten Wege.
   * @return Eine Menge von Ant-Wegen.
   */
  def computeAntWayData(nodeWaysMapping: Map[OsmNode, Set[OsmWay]]): Set[AntWayData] = {
    @tailrec
    def computeAntWayDataRec(id: Int, innerNodeWaysMapping: Map[OsmNode, Set[OsmWay]], osmNodeAntWaysMapping: Map[OsmNode, Set[AntWayData]], antWays: Set[AntWayData]): Set[AntWayData] = {
      // Das Mapping ist leer, wir sind fertig.
      if (innerNodeWaysMapping.isEmpty)
        antWays
      else {
        // der aktuell zu verarbeitende Knoten und die adjazenten Wege
        val (node, ways) = innerNodeWaysMapping.head
        // Der aktuelle Knoten hat keine adjazenten Wege mehr, weiter mit dem nächsten Knoten.
        if (ways.isEmpty)
          computeAntWayDataRec(id, innerNodeWaysMapping tail, osmNodeAntWaysMapping, antWays)
        else {
          // der aktuell zu verarbeitende Weg
          val way = ways.head
          // Start- und End-Indices ausgehend von dem aktuellen Knoten berechnen. Diese werden benutzt, um einen Knoten-Segment aus dem aktuellen Weg auszuschneiden und daraus einen Ant-Weg zu erstellen.
          val (startNodeIndex, endNodeIndex) = {
            // Der aktuelle Knoten ist nicht der End-Knoten des Weges.
            if (!way.isEndNode(node)) {
              startAndEndNodeIndexForward(way, node, nodeWaysMapping)
            }
            // Der aktuelle Knoten ist der End-Knoten des Weges.
            else {
              val (startNodeIndex, endNodeIndex) = startAndEndNodeIndexBackward(way, node, nodeWaysMapping)
              // Wenn der aktuelle Weg ein Kreis-Weg ist und der aktuelle Knoten der Anfangsknoten des Weges ist,
              // dann ist eine Sonderprüfung nötig. Es wird geprüft, ob das letzte Segment des Weges schon
              // verarbeitet wurde. Falls das der Fall sein sollte, wird mit dem aktuellen Knoten "manuell" auf das
              // erste Segment umgeschaltet und dessen Start- und End-Knoten-Index berechnet.
              val nodes = way.nodes.slice(startNodeIndex, endNodeIndex + 1)
              if (!(way.isCircle && way.isStartNode(node) && ((osmNodeAntWaysMapping.contains(nodes.head) && osmNodeAntWaysMapping(nodes.head).find(_.nodes.containsSlice(nodes)).isDefined) || (osmNodeAntWaysMapping.contains(nodes.last) && osmNodeAntWaysMapping(nodes.last).find(_.nodes.containsSlice(nodes)).isDefined) || (osmNodeAntWaysMapping.contains(nodes.head) && osmNodeAntWaysMapping.contains(nodes.last) && osmNodeAntWaysMapping(nodes.head) == osmNodeAntWaysMapping(nodes.last)))))
                (startNodeIndex, endNodeIndex)
              else
                startAndEndNodeIndexForward(way, node, nodeWaysMapping)
            }
          }
          // Die aktuell zu verarbeitende Knoten-Sequenz besteht aus Knoten zwischen dem berechneten Start- und End-Index.
          val nodes = way.nodes.slice(startNodeIndex, endNodeIndex + 1)
//          assert(nodes.nonEmpty, way.id)
          // TODO Prüfen, was zu tun ist, wenn nodes nur ein Element enthält
          // Verarbeitung der berechneten Knoten-Sequenz. Es werden fünf mögliche Fälle unterschieden.
          // Fall 1: Die Knoten-Sequenz ist bereits in einem anderen Ant-Weg enthalten.
          val (newWays, newAntWays, oldWays) = if ((osmNodeAntWaysMapping.contains(nodes.head) && osmNodeAntWaysMapping(nodes.head).find(_.nodes.containsSlice(nodes)).isDefined) || (osmNodeAntWaysMapping.contains(nodes.last) && osmNodeAntWaysMapping(nodes.last).find(_.nodes.containsSlice(nodes)).isDefined) || (osmNodeAntWaysMapping.contains(nodes.head) && osmNodeAntWaysMapping.contains(nodes.last) && osmNodeAntWaysMapping(nodes.head) == osmNodeAntWaysMapping(nodes.last)))
            (Set[AntWayData](), antWays, Set[AntWayData]())
          // Fall 2: Die Knoten-Sequenz ist das Verbindungsstück zwischen zwei bereits existierenden Ant-Wegen.
          else if (osmNodeAntWaysMapping.contains(nodes.head) && osmNodeAntWaysMapping(nodes.head).size == 1 && osmNodeAntWaysMapping(nodes.head).find(w => w.isExtendable(nodes.head)(nodeWaysMapping)).isDefined && osmNodeAntWaysMapping.contains(nodes.last) && osmNodeAntWaysMapping(nodes.last).size == 1 && osmNodeAntWaysMapping(nodes.last).find(w => w.isExtendable(nodes.last)(nodeWaysMapping)).isDefined) {
            // Beide Wege ermitteln, die verbunden werden sollen.
            val oldWay1 = osmNodeAntWaysMapping(nodes.head).head
            val oldWay2 = osmNodeAntWaysMapping(nodes.last).head
            // Weg erweitern
            val newWays = Set(oldWay1.extend(nodes, way.maxSpeed).extend(oldWay2.nodes, way.maxSpeed))
            // oldWays musste explizit erstellt werden.
            // Bei (newWays, antWays -- oldWays ++ newWays, Set(oldWay1, oldWay2)) hat der Compiler gemeckert (recursive value ... needs type)
            val oldWays = Set(oldWay1, oldWay2)
            (newWays, antWays -- oldWays ++ newWays, oldWays)
          }
          // Fall 3: Ein oder mehrere bereits vorhandene Wege können am Kopf um die Knoten-Sequens erweitert werden.
          else if (osmNodeAntWaysMapping.contains(nodes.head) && osmNodeAntWaysMapping(nodes.head).find(_.isExtendable(nodes.head)(nodeWaysMapping)).isDefined) {
            val oldWays = osmNodeAntWaysMapping(nodes.head).filter(_.isExtendable(nodes.head)(nodeWaysMapping))
            val newWays = oldWays.map(_.extend(nodes, way.maxSpeed))
            (newWays, antWays -- oldWays ++ newWays, oldWays)
          // Fall 4: Ein oder mehrere bereits vorhandene Wege können am Ende um die Knoten-Sequens erweitert werden.
          } else if (osmNodeAntWaysMapping.contains(nodes.last) && osmNodeAntWaysMapping(nodes.last).find(_.isExtendable(nodes.last)(nodeWaysMapping)).isDefined) {
            val oldWays = osmNodeAntWaysMapping(nodes.last).filter(_.isExtendable(nodes.last)(nodeWaysMapping))
            val newWays = oldWays.map(_.extend(nodes, way.maxSpeed))
            (newWays, antWays -- oldWays ++ newWays, oldWays)
          // Fall 5: Keiner der oberen Fälle trifft zu. Es muss ein neuer Weg erstellt werden.
          } else {
            val antWay = AntWayData(way.maxSpeed, nodes, way.isInstanceOf[OsmOneWay])
            (Set(antWay), antWays + antWay, Set[AntWayData]())
          }
          // Mapping aktualisieren, sodass neu berechnte Wege die alten Wege ersetzen.
          val updatedAntNodeAntWaysMapping = osmNodeAntWaysMapping ++ oldWays.flatMap { ow =>
            val nodeWaysMappingToUpdate = osmNodeAntWaysMapping.filter { case (n, ws) => ws.contains(ow) }
            val newWay = newWays.find(_.containsSlice(ow.nodes)).get
            nodeWaysMappingToUpdate.map {
              case (n, ws) => n -> (osmNodeAntWaysMapping.getOrElse(n, Set[AntWayData]()) - ow + newWay)
            }
          }
          // Mapping um die neu berechneten Wege und dessen Start- und End-Knoten ergänzen
          val newAntNodeAntWaysMapping = (updatedAntNodeAntWaysMapping ++ newWays.map { nw =>
            val startNode = nw.nodes.head
            startNode -> (updatedAntNodeAntWaysMapping.getOrElse(startNode, Set.empty[AntWayData]) + nw)
          } ++ newWays.map { nw =>
            val endNode = nw.nodes.last
            endNode -> (updatedAntNodeAntWaysMapping.getOrElse(endNode, Set.empty[AntWayData]) + nw)
          })
            // leere Einträge entfernen
            .filterNot(_._2.isEmpty)
          computeAntWayDataRec(id + 1, innerNodeWaysMapping + (node -> (ways - way)), newAntNodeAntWaysMapping, newAntWays)
        }
      }
    }
    info("Computing ant ways")
    val (time, ways) = TimeHelpers.calcTime(computeAntWayDataRec(0, nodeWaysMapping, Map[OsmNode, Set[AntWayData]](), Set[AntWayData]()))
    info("%d ant ways computed in %d ms".format(ways.size, time))
    ways
  }

  def computeAntWays(wayData: Set[AntWayData]) {
    _ways = (1 to wayData.size).zip(wayData).map {
      case (id, wd) => AntWay(id.toString, wd.nodes, wd.maxSpeed, wd.isInstanceOf[AntOneWayData])
    }.toSet
    assert(_ways.size == wayData.size)
    assert(_ways.map(_.id).toSet.size == _ways.size, "%s ids, %s ways".format(_ways.map(_.id).toSet.size, _ways.size))
  }

  /**
   * Berechnet die eingehenden und ausgehenden Wege pro Knoten.
   *
   * @return 2-Tupel. Das erste Element repräsentiert die eingehenden, das zweite Element die ausgehenden Wege. Die Datenstruktur ist jeweils eine Map, deren Schlüssel die einzelnen Knoten und die Werte die zugehörigen Wege sind.
   */
  def computeIncomingAndOutgoingWays() {
    assert(_ways != null)
    info("Computing incoming and outgoing ways")
    @tailrec
    def computeIncomingAndOutgoingWaysRec(ways: Set[AntWay], incomingWays: Map[Node, Set[AntWay]],
        outgoingWays: Map[Node, Set[AntWay]]): (Map[Node, Set[AntWay]], Map[Node, Set[AntWay]]) = {
      if (ways.isEmpty)
        (incomingWays, outgoingWays)
      else {
        // eingehende Wege
        val newIncomingWays = {
          ways.head match {
            // Einbahn-Strasse
            case oneWay: AntOneWay => {
              // nur der End-Knoten enthält einen eingehenden Weg
              Map(oneWay.nodes.last -> (incomingWays.getOrElse(oneWay.nodes.last, Set.empty[AntWay]) + oneWay))
            }
            // normaler Weg
            case way: AntWay => {
              // sowohl der Start- als auch der End-Knoten enthalten eingehende Wege
              Map(
                way.nodes.head -> (incomingWays.getOrElse(way.nodes.head, Set.empty[AntWay]) + way),
                way.nodes.last -> (incomingWays.getOrElse(way.nodes.last, Set.empty[AntWay]) + way))
            }
          }
        }
        // ausgehende Wege
        val newOutgoingWays = {
          ways.head match {
            // Einbahn-Strasse
            case oneWay: AntOneWay => {
              // nur der Start-Knoten enthält einen ausgehenden Weg
              Map(oneWay.nodes.head -> (outgoingWays.getOrElse(oneWay.nodes.head, Set.empty[AntWay]) + oneWay))
            }
            // normaler Weg
            case way: AntWay => {
              // sowohl der Start- als auch der End-Knoten enthalten ausgehende Wege
              Map(
                way.nodes.head -> (outgoingWays.getOrElse(way.nodes.head, Set[AntWay]()) + way),
                way.nodes.last -> (outgoingWays.getOrElse(way.nodes.last, Set[AntWay]()) + way))
            }
          }
        }
        computeIncomingAndOutgoingWaysRec(ways.tail, incomingWays ++ newIncomingWays, outgoingWays ++ newOutgoingWays)
      }
    }
    val (incomingWays, outgoingWays) = computeIncomingAndOutgoingWaysRec(_ways.toSet, Map[Node, Set[AntWay]](),
      Map[Node, Set[AntWay]]())
    _incomingWays = incomingWays
    _outgoingWays = outgoingWays
  }

  def computeNodes(wayData: Set[AntWayData]) {
    wayData.foreach { wd =>
      _nodes += wd.nodes.head.asInstanceOf[Node] += wd.nodes.last.asInstanceOf[Node]
    }
  }

  /**
   * Berechnet die Quell- und die Zielknoten mit Hilfe der ein- und ausgehenden Wege.
   *
   * @param incomingWays Map, in der die Knoten-Ids als Schlüssel und Ids der eingehenden Wege als Wert gespeichert sind.
   * @param outgoingWays Map, in der die Knoten-Ids als Schlüssel und Ids der ausgehenden Wege als Wert gespeichert sind.
   */
  def computeSourcesAndDestinations() {
    assert(_nodes != null)
    assert(_incomingWays != null)
    assert(_outgoingWays != null)
    info("Computing sources and destinations")
    val sources = new ListBuffer[Node]
    val destinations = new ListBuffer[Node]
    val (time, _) = TimeHelpers.calcTime {
      _nodes.foreach {
        n => {
          // Wenn ein Knoten eingehende Wege hat, kann er als Ziel dienen
          if (_incomingWays.contains(n)) n +=: destinations
          // Wenn ein Knoten ausgehende Wege hat, kann er als Quelle dienen
          if (_outgoingWays.contains(n)) n +=: sources
        }
      }
    }
    info("%d sources and %d destinations computed in %d ms".format(sources.size, destinations.size, time))
    _sources = sources.toSet
    _destinations = destinations.toSet
  }

  def destinations = _destinations

  def incomingWays = _incomingWays

  def nodes = _nodes

  def outgoingWays = _outgoingWays

  def prepare = {
    info("Preparing")
    val nodeWaysMapping = OsmMap.nodeWaysByHighwayMapping(relevantHighways).par.filter {
      case (node, ways) => ways.size >= 2
    }.seq
    computeAntWayData(nodeWaysMapping)
  }

  lazy val relevantHighways = {
    val relevantHighwaysValue = Props get ("antMap.relevantHighways", DefaultRelevantHighways)
    val untrimmedRelevantHighways = relevantHighwaysValue.split(',')
    val trimmedRelevantHighways = untrimmedRelevantHighways.map(_.trim)
    trimmedRelevantHighways.toSet
  }

  def sources = _sources

  def startAndEndNodeIndexBackward(way: OsmWay, node: OsmNode, nodeWaysMapping: Map[OsmNode, Set[OsmWay]]) = {
    // End-Index ist der Index des aktuellen Knoten. Der passende Start-Index muss gesucht werden.
    // Index des letzten Verbindungsknotens vom End-Knoten des Weges aus suchen.
    val index = way.nodes.lastIndexWhere(nodeWaysMapping.contains(_), way.nodes.size - 2)
    // Wenn kein passender Knoten gefunden wird, des Index des Start-Knotens verwenden.
    val startNodeIndex = if (index != -1) index else 0
    val endNodeIndex = way.nodes.lastIndexOf(node)
    (startNodeIndex, endNodeIndex)
  }

  def startAndEndNodeIndexForward(way: OsmWay, node: OsmNode, nodeWaysMapping: Map[OsmNode, Set[OsmWay]]) = {
    // Start-Index ist der Index des aktuellen Knoten. Der passende End-Index muss gesucht werden.
    val startNodeIndex = way.nodes.indexOf(node)
    assert(startNodeIndex > -1)
    // passenden End-Knoten-Index (Verbindungs-Knoten zwischen mehreren Wegen) suchen
    val index = way.nodes.indexWhere(nodeWaysMapping.contains(_), startNodeIndex + 1)
    // Wenn kein passender Knoten gefunden wird, den Index des End-Knotens verwenden.
    val endNodeIndex = if (index != -1) index else way.nodes.size - 1
    (startNodeIndex, endNodeIndex)
  }

  def ways = _ways
}
