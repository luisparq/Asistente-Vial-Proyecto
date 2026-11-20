package com.tecsup.asistente.conduccion.feature.map

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

internal fun roadLayerIds(layer: RoadLayer): Array<String> = when (layer) {
    RoadLayer.SPEED -> arrayOf("road-limit-lines")
    RoadLayer.INCIDENTS -> arrayOf("${layer.sourceId}-points", "${layer.sourceId}-lines")
    else -> arrayOf("${layer.sourceId}-points")
}

internal fun addRoadMapLayers(style: Style) {
    RoadLayer.entries.filter { it != RoadLayer.FLOW && it != RoadLayer.SPEED }.forEach { layer ->
        val color = when (layer) {
            RoadLayer.INCIDENTS, RoadLayer.HOSPITAL -> Color.rgb(205, 55, 58)
            RoadLayer.SCHOOLS -> Color.rgb(138, 79, 202)
            RoadLayer.POLICE -> Color.rgb(42, 117, 224)
            RoadLayer.FUEL -> Color.rgb(208, 137, 30)
            else -> Color.rgb(31, 145, 121)
        }
        style.addSource(GeoJsonSource(layer.sourceId, EMPTY_ROAD_FEATURES))
        if (layer == RoadLayer.INCIDENTS) {
            style.addLayer(LineLayer("${layer.sourceId}-lines", layer.sourceId).withProperties(lineColor(color), lineWidth(5f)))
        }
        style.addLayer(CircleLayer("${layer.sourceId}-points", layer.sourceId).withProperties(
            circleColor(color), circleRadius(8f), circleStrokeColor(Color.WHITE), circleStrokeWidth(2f)
        ))
    }
}

internal fun updateRoadMapLayers(style: Style, layers: Map<RoadLayer, RoadLayerData>, selected: Set<RoadLayer>) {
    RoadLayer.entries.filter { it != RoadLayer.FLOW }.forEach { layer ->
        val data = layers[layer] ?: RoadLayerData()
        val features = if (layer == RoadLayer.SPEED) speedFeatures(data.catalog) else data.features
        style.getSourceAs<GeoJsonSource>(layer.sourceId)?.setGeoJson(features)
        roadLayerIds(layer).forEach { id ->
            style.getLayer(id)?.setProperties(visibility(if (layer in selected) Property.VISIBLE else Property.NONE))
        }
    }
}

internal fun updateTrafficLayer(style: Style, template: String?) {
    if (style.getLayer("tomtom-flow-raster") != null) style.removeLayer("tomtom-flow-raster")
    if (style.getSource(RoadLayer.FLOW.sourceId) != null) style.removeSource(RoadLayer.FLOW.sourceId)
    if (template != null) {
        style.addSource(RasterSource(RoadLayer.FLOW.sourceId, TileSet("2.1.0", template), 256))
        style.addLayerBelow(RasterLayer("tomtom-flow-raster", RoadLayer.FLOW.sourceId)
            .withProperties(rasterOpacity(0.7f)), "${RoadLayer.INCIDENTS.sourceId}-lines")
    }
}

private fun speedFeatures(catalog: RoadCatalog?): String {
    val features = JSONArray()
    catalog?.speedSections.orEmpty().forEach { section ->
        features.put(JSONObject().put("type", "Feature")
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates", JSONArray()
                .put(JSONArray().put(section.start.lon).put(section.start.lat))
                .put(JSONArray().put(section.end.lon).put(section.end.lat))))
            .put("properties", JSONObject().put("road_label", "Límite: ${section.kmh} km/h · OpenStreetMap")))
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}
