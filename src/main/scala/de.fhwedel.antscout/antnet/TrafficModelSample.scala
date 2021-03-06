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
package antnet

import collection.mutable.Buffer
import extensions.ExtendedDouble._

/**
 * Stich-Proben für das lokale statistische Modell.
 */
class TrafficModelSample {

  /**
   * Mittel-Wert.
   */
  private var mean = 0.0

  /**
   * Reise-Zeiten.
   */
  private val _tripTimes = Buffer[Double]()

  /**
   * Varianz.
   */
  private var variance = 0.0

  /**
   * Fügt eine Reise-Zeit hinzu.
   *
   * @param tripTime Reise-Zeit
   */
  def +=(tripTime: Double) {
    tripTime +=: _tripTimes
    // Die älteste Reise-Zeit entfernen, falls nötig.
    if (_tripTimes.size > Settings.Wmax) {
      _tripTimes -= _tripTimes.last
    }
    // Mittel-Wert aktualisieren
    mean += Settings.Rho * (tripTime - mean)
    // Varianz aktualisieren
    variance += Settings.Rho * (math.pow(tripTime - mean, 2) - variance)
  }

  /**
   * Berechnet die beste (kleinste) Fahrzeit.
   *
   * @return Die beste Fahrzeit.
   */
  def bestTripTime = _tripTimes min

  /**
   * Berechnet die Verstärkung, die für die Pheromon-Aktualisierung genutzt wird. Am Ende wird "künstlich" dafür
   * gesorgt, dass die Verstärkung zwischen (exklusive) 0 und 1 liegt. Eine Verstärkung = 0 oder = 1 würde dafür
   * sorgen, dass einzelne Pheromone = 0 werden. Das würde wiederum dafür sorgen, dass die entsprechenden
   * Wahrscheinlichkeiten = 0 werden.
   *
   * @param tripTime Aktuelle Fahrzeit
   * @param neighbourCount Anzahl der Nachbarn, die vom aktuellen Knoten erreicht werden können.
   * @return Errechnete Verstärkung
   */
  def reinforcement(tripTime: Double, neighbourCount: Double) = {
    val iInf = bestTripTime
    val iSup = mean + Settings.Z * math.sqrt(variance / Settings.Wmax)
    val stabilityTerm = (iSup - iInf) + (tripTime - iInf)
    val r = Settings.C1 * (bestTripTime / tripTime) + Settings.C2 * (if (stabilityTerm ~> 0.0) ((iSup - iInf) /
      stabilityTerm)
    else
      0)
    // TODO Prüfen, ob die Werte <= 0 und > 1 durch Rechenfehler zustande kommen
    val squashedReinforcement = TrafficModelSample.transformBySquash(math.max(0.05, math.min(r, 0.95)),
      neighbourCount)
    assert(squashedReinforcement ~> 0 && (squashedReinforcement ~< 1 || (neighbourCount == 1 && (squashedReinforcement
      ~= 1))), "Squashed reinforcement: %f, neighbour count: %d" format (squashedReinforcement, neighbourCount))
    squashedReinforcement
  }

  /**
   * Reise-Zeiten.
   *
   * @return Reise-Zeiten
   */
  def tripTimes = _tripTimes
}

/**
 * TrafficModelSample-Factory.
 */
object TrafficModelSample {

  /**
   * Erzeugt eine neue [[de.fhwedel.antscout.antnet.TrafficModelSample]]-Instanz.
   *
   * @return [[de.fhwedel.antscout.antnet.TrafficModelSample]]-Instanz
   */
  def apply() = new TrafficModelSample()

  /**
   * Laut Literatur eigentlich (1 + ...)^(-1). Wenn aber die Transformation nicht mit s(r) / s(1) sondern mit s(1) /
   * s(r) aufgerufen wird, kann (...)^(-1) weggelassen werden.
   *
   * @param x Wert, der transformiert werden soll.
   * @param neighbourCount Anzahl der Nachbarn.
   * @param a a.
   * @return Transformierter Wert.
   */
  def squash(x: Double, neighbourCount: Double, a: Double = Settings.A) = {
    require((x ~> 0) && (x ~<= 1), "x: %f".format(x))
    1 + math.exp(a / (x * neighbourCount))
  }

  /**
   * Laut Literatur eigentlich s(r) / s(1). Wenn aber in s(x) das (...)^(-1) weggelassen wird,
   * wird aus s(r) / s(1) (s1) / s(r).
   *
   * @param x Wert, der transformiert werden soll.
   * @param N Anzahl der Nachbarn.
   * @param a a.
   * @return Transformierter Wert.
   */
  def transformBySquash(x: Double, N: Double, a: Double = Settings.A) = squash(1, N, a) / squash(x, N, a)
}
