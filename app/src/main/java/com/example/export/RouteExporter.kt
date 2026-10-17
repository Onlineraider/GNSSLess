package com.example.export

import com.example.data.PathPointEntity
import com.example.data.RouteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates standard GPX 1.1 and KML 2.2 formats for offline route export (P54).
 */
object RouteExporter {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun toGpx(route: RouteEntity, points: List<PathPointEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<gpx version=\"1.1\" creator=\"Inertial Path Tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(route.title)}</name>")
        sb.appendLine("    <time>${isoFormat.format(Date(route.startTime))}</time>")
        sb.appendLine("    <desc>Mode: ${route.mode}, Distance: ${route.totalDistanceMeters}m, Est. Error: ±${route.finalErrorRadiusMeters}m</desc>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(route.title)}</name>")
        sb.appendLine("    <trkseg>")

        for (pt in points) {
            sb.appendLine("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
            sb.appendLine("        <ele>${pt.altitude}</ele>")
            sb.appendLine("        <time>${isoFormat.format(Date(pt.timestamp))}</time>")
            sb.appendLine("        <extensions>")
            sb.appendLine("          <speed>${pt.speedMps}</speed>")
            sb.appendLine("          <course>${pt.courseDeg}</course>")
            sb.appendLine("          <errorRadius>${pt.errorRadiusMeters}</errorRadius>")
            sb.appendLine("          <zupt>${pt.isZupt}</zupt>")
            if (pt.isGpsComparison && pt.gpsLatitude != null) {
                sb.appendLine("          <gpsGroundTruth lat=\"${pt.gpsLatitude}\" lon=\"${pt.gpsLongitude}\" ele=\"${pt.gpsAltitude}\" acc=\"${pt.gpsAccuracy}\" errDist=\"${pt.errorDistanceMeters}\"/>")
            }
            sb.appendLine("        </extensions>")
            sb.appendLine("      </trkpt>")
        }

        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun toKml(route: RouteEntity, points: List<PathPointEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
        sb.appendLine("  <Document>")
        sb.appendLine("    <name>${escapeXml(route.title)}</name>")
        sb.appendLine("    <description>Mode: ${route.mode}, Distance: ${route.totalDistanceMeters}m</description>")
        sb.appendLine("    <Style id=\"inertialTrackStyle\">")
        sb.appendLine("      <LineStyle>")
        sb.appendLine("        <color>ffF8BD38</color>") // Cyan / Blue track
        sb.appendLine("        <width>4</width>")
        sb.appendLine("      </LineStyle>")
        sb.appendLine("    </Style>")
        sb.appendLine("    <Placemark>")
        sb.appendLine("      <name>Inertial Path</name>")
        sb.appendLine("      <styleUrl>#inertialTrackStyle</styleUrl>")
        sb.appendLine("      <LineString>")
        sb.appendLine("        <extrude>1</extrude>")
        sb.appendLine("        <tessellate>1</tessellate>")
        sb.appendLine("        <altitudeMode>absolute</altitudeMode>")
        sb.appendLine("        <coordinates>")

        val coordStrings = points.joinToString(" ") { pt ->
            "${pt.longitude},${pt.latitude},${pt.altitude}"
        }
        sb.appendLine("          $coordStrings")

        sb.appendLine("        </coordinates>")
        sb.appendLine("      </LineString>")
        sb.appendLine("    </Placemark>")
        sb.appendLine("  </Document>")
        sb.appendLine("</kml>")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
