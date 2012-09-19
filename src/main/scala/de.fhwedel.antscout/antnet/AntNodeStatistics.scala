package de.fhwedel.antscout
package antnet

import collection.mutable

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 18.09.12
 * Time: 12:49
 */

class AntNodeStatistics {

  val antAges = mutable.Buffer[Long]()
  var deadEndStreetReachedAnts = 0
  var destinationReachedAnts = 0
  var launchedAnts = 0
  var maxAgeExceededAnts = 0
  var processedAnts = 0

  def prepare = {
    AntNode.Statistics(
      antAge = if (antAges.size > 0) antAges.sum / antAges.size else 0,
      deadEndStreetReachedAnts = deadEndStreetReachedAnts,
      destinationReachedAnts = destinationReachedAnts,
      launchedAnts = launchedAnts,
      maxAgeExceededAnts = maxAgeExceededAnts,
      processedAnts = processedAnts
    )
  }

  /**
   * Resettet die Statistik.
   */
  def reset() {
    antAges.clear()
    processedAnts = 0
  }
}
