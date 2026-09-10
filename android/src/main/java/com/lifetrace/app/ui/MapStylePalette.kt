package com.lifetrace.app.ui

import java.util.Locale
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth

/**
 * Converts the detailed Bright vector style into a softer dark palette at runtime.
 *
 * This operates on map style layers rather than applying a View-level color matrix,
 * so LifeTrace markers, the diary track and Compose overlays keep their own colors.
 */
internal fun applySoftDarkPalette(style: Style) {
    style.layers.forEach { layer ->
        val id = layer.id.lowercase(Locale.ROOT)
        runCatching {
            when (layer) {
                is BackgroundLayer -> layer.setProperties(backgroundColor(SOFT_DARK_BACKGROUND))

                is FillLayer -> {
                    val color = when {
                        "water" in id -> SOFT_DARK_WATER
                        "park" in id || "grass" in id || "wood" in id || "cemetery" in id -> SOFT_DARK_GREEN
                        "commercial" in id || "hospital" in id -> SOFT_DARK_WARM
                        "industrial" in id || "sand" in id -> SOFT_DARK_EARTH
                        "building" in id -> SOFT_DARK_BUILDING
                        "aeroway" in id -> SOFT_DARK_SURFACE_HIGH
                        "residential" in id || "suburb" in id || "railway" in id -> SOFT_DARK_SURFACE
                        else -> SOFT_DARK_SURFACE
                    }
                    layer.setProperties(fillColor(color))
                }

                is LineLayer -> {
                    val color = when {
                        "water" in id || "ferry" in id -> SOFT_DARK_WATER_LINE
                        "boundary" in id -> SOFT_DARK_BOUNDARY
                        "railway" in id -> SOFT_DARK_RAIL
                        "motorway" in id || "trunk" in id || "primary" in id -> SOFT_DARK_ROAD_MAJOR
                        "secondary" in id || "tertiary" in id || "link" in id -> SOFT_DARK_ROAD_MEDIUM
                        "highway" in id || "road" in id || "tunnel" in id || "bridge" in id ||
                            "aeroway" in id || "cablecar" in id -> SOFT_DARK_ROAD_MINOR
                        else -> null
                    }
                    color?.let { layer.setProperties(lineColor(it)) }
                }

                is SymbolLayer -> {
                    val color = when {
                        "water" in id -> SOFT_DARK_WATER_TEXT
                        "highway" in id || "road" in id -> SOFT_DARK_TEXT_MUTED
                        else -> SOFT_DARK_TEXT
                    }
                    layer.setProperties(
                        textColor(color),
                        textHaloColor(SOFT_DARK_BACKGROUND),
                        textHaloWidth(1.15f),
                    )
                }
            }
        }
    }
}

private const val SOFT_DARK_BACKGROUND = "#171B22"
private const val SOFT_DARK_SURFACE = "#20262F"
private const val SOFT_DARK_SURFACE_HIGH = "#272E38"
private const val SOFT_DARK_WATER = "#193247"
private const val SOFT_DARK_WATER_LINE = "#35637E"
private const val SOFT_DARK_WATER_TEXT = "#8ABAD7"
private const val SOFT_DARK_GREEN = "#20382F"
private const val SOFT_DARK_WARM = "#34272D"
private const val SOFT_DARK_EARTH = "#383328"
private const val SOFT_DARK_BUILDING = "#2B313A"
private const val SOFT_DARK_BOUNDARY = "#5B6574"
private const val SOFT_DARK_RAIL = "#59616D"
private const val SOFT_DARK_ROAD_MAJOR = "#786652"
private const val SOFT_DARK_ROAD_MEDIUM = "#635A4E"
private const val SOFT_DARK_ROAD_MINOR = "#47505B"
private const val SOFT_DARK_TEXT = "#D9DEE7"
private const val SOFT_DARK_TEXT_MUTED = "#B6BEC9"
