package de.fhwedel.antscout
package antnet

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import osm.{OsmWay, GeographicCoordinate, OsmNode}

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 03.12.11
 * Time: 09:42
 */

class AntMapTest extends FunSuite with ShouldMatchers {
  test("convertOsmWayToAntWays") {
    val osmNode1 = new OsmNode(1, new GeographicCoordinate(1, 1))
    val osmNode2 = new OsmNode(2, new GeographicCoordinate(2, 2))
    val osmNode3 = new OsmNode(3, new GeographicCoordinate(3, 3))
    val osmWay = new OsmWay(1, "", Vector(osmNode1, osmNode2, osmNode3), 0)
    val antNode1 = AntNode(1)
    val antNode3 = AntNode(3)
    val antNodes = Map(1 -> antNode1, 3 -> antNode3)
    val antWays = AntMap.convertOsmWayToAntWays(osmWay, antNodes)
    antWays should have size (1)
    antWays.head.id should be ("1-1")
    antWays.head.startNode.id should be (1)
    antWays.head.endNode.id should be (3)
  }
  
  test("incomingWays, 1") {
    val node1 = AntNode(1)
    val node2 = AntNode(2)
    val node3 = AntNode(3)
    val node4 = AntNode(4)
    val nodes = Iterable(node1, node2, node3, node4)
    val way1 = AntWay(1, node1, node2)
    val way2 = AntWay(2, node2, node3)
    val way3 = AntOneWay(3, node4, node2).asInstanceOf[AntWay]
    val ways = Iterable(way1, way2, way3)
    val antMap = AntMap(nodes, ways)
    antMap.incomingWays should have size (4)
    antMap.incomingWays(node1) should have size (1)
    antMap.incomingWays(node1) should contain (way1)
    antMap.incomingWays(node2) should have size (3)
    antMap.incomingWays(node2) should contain (way1)
    antMap.incomingWays(node2) should contain (way2)
    antMap.incomingWays(node2) should contain (way3)
    antMap.incomingWays(node3) should have size (1)
    antMap.incomingWays(node3) should contain (way2)
    antMap.incomingWays(node4) should have size (0)
  }

  test("incomingWays, 2") {
    val node1 = AntNode(1)
    val node2 = AntNode(2)
    val node3 = AntNode(3)
    val node4 = AntNode(4)
    val nodes = Iterable(node1, node2, node3, node4)
    val way1 = AntWay(1, node1, node2)
    val way2 = AntWay(2, node2, node3)
    val way3 = AntOneWay(3, node2, node4).asInstanceOf[AntWay]
    val ways = Iterable(way1, way2, way3)
    val antMap = AntMap(nodes, ways)
    antMap.incomingWays should have size (4)
    antMap.incomingWays(node1) should have size (1)
    antMap.incomingWays(node1) should contain (way1)
    antMap.incomingWays(node2) should have size (2)
    antMap.incomingWays(node2) should contain (way1)
    antMap.incomingWays(node2) should contain (way2)
    antMap.incomingWays(node3) should have size (1)
    antMap.incomingWays(node3) should contain (way2)
    antMap.incomingWays(node4) should have size (1)
    antMap.incomingWays(node4) should contain (way3)
  }

  test("outgoingWays, 1") {
    val node1 = AntNode(1)
    val node2 = AntNode(2)
    val node3 = AntNode(3)
    val node4 = AntNode(4)
    val nodes = Iterable(node1, node2, node3, node4)
    val way1 = AntWay(1, node1, node2)
    val way2 = AntWay(2, node2, node3)
    val way3 = AntOneWay(3, node4, node2).asInstanceOf[AntWay]
    val ways = Iterable(way1, way2, way3)
    val antMap = AntMap(nodes, ways)
    antMap.outgoingWays should have size (4)
    antMap.outgoingWays(node1) should have size (1)
    antMap.outgoingWays(node1) should contain (way1)
    antMap.outgoingWays(node2) should have size (2)
    antMap.outgoingWays(node2) should contain (way1)
    antMap.outgoingWays(node2) should contain (way2)
    antMap.outgoingWays(node3) should have size (1)
    antMap.outgoingWays(node3) should contain (way2)
    antMap.outgoingWays(node4) should have size (1)
    antMap.outgoingWays(node4) should contain (way3)
  }

  test("outgoingWays, 2") {
    val node1 = AntNode(1)
    val node2 = AntNode(2)
    val node3 = AntNode(3)
    val node4 = AntNode(4)
    val nodes = Iterable(node1, node2, node3, node4)
    val way1 = AntWay(1, node1, node2)
    val way2 = AntWay(2, node2, node3)
    val way3 = AntOneWay(3, node2, node4).asInstanceOf[AntWay]
    val ways = Iterable(way1, way2, way3)
    val antMap = AntMap(nodes, ways)
    antMap.outgoingWays should have size (4)
    antMap.outgoingWays(node1) should have size (1)
    antMap.outgoingWays(node1) should contain (way1)
    antMap.outgoingWays(node2) should have size (3)
    antMap.outgoingWays(node2) should contain (way1)
    antMap.outgoingWays(node2) should contain (way2)
    antMap.outgoingWays(node2) should contain (way3)
    antMap.outgoingWays(node3) should have size (1)
    antMap.outgoingWays(node3) should contain (way2)
    antMap.outgoingWays(node4) should have size (0)
  }
}