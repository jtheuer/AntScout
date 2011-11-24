package de.fhwedel.antscout
package openstreetmap

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 18.11.11
 * Time: 11:40
 */

class GeographicCoordinate(val latitude: Float, val longitude: Float) {
    override def toString = "%f, %f" format (latitude, longitude)
}