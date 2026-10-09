package com.teamz.lab.debugger.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Side of the square viewport every [DgIcons] vector is drawn in. */
const val DG_ICON_VIEWPORT = 24f

/** Stroke width, in viewport units, shared by every [DgIcons] vector. */
const val DG_ICON_STROKE = 2f

/**
 * Builds one icon in the DeviceGPT voice: 24 x 24 viewport, 2-unit stroke, round caps and joins, no fill
 * apart from small solid dots. The paint is black so `Icon(tint = ...)` decides the colour.
 *
 * @param name vector name, shown in tooling.
 * @param strokes SVG path data drawn as an outline.
 * @param dots SVG path data filled solid; only for dots of about one unit radius.
 */
internal fun dgIcon(name: String, strokes: String, dots: String? = null): ImageVector =
    ImageVector.Builder(
        name = "Dg.$name",
        defaultWidth = DG_ICON_VIEWPORT.dp,
        defaultHeight = DG_ICON_VIEWPORT.dp,
        viewportWidth = DG_ICON_VIEWPORT,
        viewportHeight = DG_ICON_VIEWPORT,
    ).apply {
        addPath(
            pathData = addPathNodes(strokes),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = DG_ICON_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        if (dots != null) {
            addPath(pathData = addPathNodes(dots), fill = SolidColor(Color.Black))
        }
    }.build()

/**
 * DeviceGPT's own icon set. Every vector shares one voice (see [dgIcon]) and is built on first use.
 *
 * The path data is generated from one SVG source that is also rendered to a contact sheet and checked by eye
 * at 48 px and 20 px, so do not edit a path by hand without re-rendering it.
 *
 * Generic meanings (close, check, warning, refresh, settings, search, delete, copy, play, pause) are not
 * drawn here: use the stock `androidx.compose.material.icons` icon, as [EmojiIcons] does.
 */
object DgIcons {
    /** A phone with a tick: the full device check. */
    val PhoneCheck: ImageVector by lazy {
        dgIcon(
            name = "PhoneCheck",
            strokes = "M8.5 3h7a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-13a2.5 2.5 0 0 1 2.5 -2.5z M9 12.5l2.2 2.2 3.8-4.4",
        )
    }

    /** A camera with a tick: the camera test. */
    val CameraCheck: ImageVector by lazy {
        dgIcon(
            name = "CameraCheck",
            strokes = "M3 9.5A2.5 2.5 0 0 1 5.5 7h2L9 4.5h6L16.5 7h2A2.5 2.5 0 0 1 21 9.5v8a2.5 2.5 0 0 " +
                "1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z M9 13.3l2.2 2.2 3.8-4.4",
        )
    }

    /** A microphone with a tick: the microphone test. */
    val MicCheck: ImageVector by lazy {
        dgIcon(
            name = "MicCheck",
            strokes = "M9 3h0a3 3 0 0 1 3 3v4a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-4a3 3 0 0 1 3 -3z M3 10.5a6 " +
                "6 0 0 0 12 0 M9 16.5V21 M15.5 18.5l2 2 3.5-4",
        )
    }

    /** A phone screen with one marked dot: the dead-pixel test. */
    val ScreenCheck: ImageVector by lazy {
        dgIcon(
            name = "ScreenCheck",
            strokes = "M8.5 3h7a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-13a2.5 2.5 0 0 1 2.5 -2.5z M10.4 9a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0 -5.2 0z " +
                "M10.5 17.5h3",
            dots = "M11.9 9a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z",
        )
    }

    /** A finger touching a screen, with a ripple: the touch test. */
    val TouchCheck: ImageVector by lazy {
        dgIcon(
            name = "TouchCheck",
            strokes = "M8 16H5.5A2.5 2.5 0 0 1 3 13.5v-8A2.5 2.5 0 0 1 5.5 3h13A2.5 2.5 0 0 1 21 5.5v8a2.5 " +
                "2.5 0 0 1-2.5 2.5H16 M10 21v-9.5a2 2 0 0 1 4 0V21 M8 8.2a4.6 4.6 0 0 1 8 0",
        )
    }

    /** A battery with a pulse line: battery health. */
    val BatteryHealth: ImageVector by lazy {
        dgIcon(
            name = "BatteryHealth",
            strokes = "M5.5 7h10a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1 -2.5 2.5h-10a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-5a2.5 2.5 0 0 1 2.5 -2.5z M21 11v2 M6 12h2l1.5-2 2 4 1.5-2h2",
        )
    }

    /** A battery with a bolt and an inflow chevron: charging and power flow. */
    val ChargeFlow: ImageVector by lazy {
        dgIcon(
            name = "ChargeFlow",
            strokes = "M11.5 6h6a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1 -2.5 2.5h-6a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-10a2.5 2.5 0 0 1 2.5 -2.5z M12.5 3.25h4 M15.5 9.5l-2.5 4h3.5l-2.5 4 M3.5 " +
                "10.5l3 3-3 3",
        )
    }

    /** Signal bars with a tick: the network test. */
    val NetworkCheck: ImageVector by lazy {
        dgIcon(
            name = "NetworkCheck",
            strokes = "M4 20v-3 M8.5 20v-6.5 M13 20V10 M15 6.5l2 2 3.5-4.5",
        )
    }

    /** A gauge: speed and performance. */
    val Speed: ImageVector by lazy {
        dgIcon(
            name = "Speed",
            strokes = "M3.94 18.5A9 9 0 1 1 20.06 18.5 M12 14.5l3.5-4.5",
            dots = "M10.8 14.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
        )
    }

    /** A RAM stick with its notch and three chips: memory. */
    val Memory: ImageVector by lazy {
        dgIcon(
            name = "Memory",
            strokes = "M3 16V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9 M3 16v3.5h7.5v-2h3v2H21V16 M7.5 9v3 M12 9v3 " +
                "M16.5 9v3",
        )
    }

    /** Stacked discs: storage. */
    val Storage: ImageVector by lazy {
        dgIcon(
            name = "Storage",
            strokes = "M4 7c0-1.66 3.58-3 8-3s8 1.34 8 3-3.58 3-8 3-8-1.34-8-3z M4 7v10c0 1.66 3.58 3 8 " +
                "3s8-1.34 8-3V7 M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3",
        )
    }

    /** A thermometer: temperature and heat. */
    val Temperature: ImageVector by lazy {
        dgIcon(
            name = "Temperature",
            strokes = "M10 5a2 2 0 0 1 4 0v8.54a4 4 0 1 1-4 0z M12 9.5V17",
        )
    }

    /** A shield with a tick: privacy and security. */
    val PrivacyShield: ImageVector by lazy {
        dgIcon(
            name = "PrivacyShield",
            strokes = "M12 3l7 3v5.5c0 4.5-3 8-7 9.5-4-1.5-7-5-7-9.5V6z M9 11.8l2.2 2.2 3.8-4.2",
        )
    }

    /** A page with a folded corner and a tick: the verified report or certificate. */
    val Report: ImageVector by lazy {
        dgIcon(
            name = "Report",
            strokes = "M11 21H7.5A2.5 2.5 0 0 1 5 18.5v-13A2.5 2.5 0 0 1 7.5 3H13l5 5v3.5 M13 3v5h5 M8.5 " +
                "13h3 M13.5 17.5l2.2 2.2 4-4.6",
        )
    }

    /** A three-step podium: the leaderboard. */
    val Leaderboard: ImageVector by lazy {
        dgIcon(
            name = "Leaderboard",
            strokes = "M3 21v-8h6V8h6v8h6v5z M9 13v8 M15 16v5",
            dots = "M10.9 4a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z",
        )
    }

    /** A simple flame: the daily streak. */
    val Streak: ImageVector by lazy {
        dgIcon(
            name = "Streak",
            strokes = "M12 3c.5 3.5-5.5 5.5-5.5 11a5.5 5.5 0 0 0 11 0c0-2.3-1-4-2.2-5.2-.3 1.6-1 2.4-2 " +
                "2.7C13.6 9 13.6 5.5 12 3z",
        )
    }

    /** A chat bubble with a spark: AI help. */
    val AiHelp: ImageVector by lazy {
        dgIcon(
            name = "AiHelp",
            strokes = "M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v8a2.5 2.5 0 0 1-2.5 2.5H12l-4 " +
                "3.5V17H6.5A2.5 2.5 0 0 1 4 14.5z M12 7.5c.45 2 1 2.55 3 3-2 .45-2.55 1-3 " +
                "3-.45-2-1-2.55-3-3 2-.45 2.55-1 3-3z",
        )
    }

    /** An app tile with a plus: App Doctor. */
    val AppDoctor: ImageVector by lazy {
        dgIcon(
            name = "AppDoctor",
            strokes = "M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1 -4 4h-8a4 4 0 0 1 -4 -4v-8a4 4 0 0 1 4 -4z M12 " +
                "8.5v7 M8.5 12h7",
        )
    }

    /** An arrow leaving a score ring: share the score. */
    val ShareScore: ImageVector by lazy {
        dgIcon(
            name = "ShareScore",
            strokes = "M10 8a6 6 0 1 0 6 6 M11 13l9-9 M14.5 4H20v5.5",
        )
    }

    /** A grid of tiles with one highlighted: the home-screen widget. */
    val Widget: ImageVector by lazy {
        dgIcon(
            name = "Widget",
            strokes = "M6 4h2.5a2 2 0 0 1 2 2v2.5a2 2 0 0 1 -2 2h-2.5a2 2 0 0 1 -2 -2v-2.5a2 2 0 0 1 2 -2z " +
                "M15.5 4h2.5a2 2 0 0 1 2 2v2.5a2 2 0 0 1 -2 2h-2.5a2 2 0 0 1 -2 -2v-2.5a2 2 0 0 1 2 " +
                "-2z M6 13.5h2.5a2 2 0 0 1 2 2v2.5a2 2 0 0 1 -2 2h-2.5a2 2 0 0 1 -2 -2v-2.5a2 2 0 0 1 " +
                "2 -2z M15.95 14.75h1.6a1.2 1.2 0 0 1 1.2 1.2v1.6a1.2 1.2 0 0 1 -1.2 1.2h-1.6a1.2 1.2 " +
                "0 0 1 -1.2 -1.2v-1.6a1.2 1.2 0 0 1 1.2 -1.2z",
            dots = "M15.55 16.75a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
        )
    }

    /** A phone with exchange arrows: the buy/sell check. */
    val BuySell: ImageVector by lazy {
        dgIcon(
            name = "BuySell",
            strokes = "M6.5 4h3a2.5 2.5 0 0 1 2.5 2.5v11a2.5 2.5 0 0 1 -2.5 2.5h-3a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-11a2.5 2.5 0 0 1 2.5 -2.5z M15 9h6 M19 7l2 2-2 2 M21 15h-6 M17 13l-2 2 2 2",
        )
    }

    /** A screen on a stand: display. */
    val Display: ImageVector by lazy {
        dgIcon(
            name = "Display",
            strokes = "M5.5 4h13a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1 -2.5 2.5h-13a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-8a2.5 2.5 0 0 1 2.5 -2.5z M12 17v4 M8.5 21h7",
        )
    }

    /** A processor with pins: CPU. */
    val Cpu: ImageVector by lazy {
        dgIcon(
            name = "Cpu",
            strokes = "M8.5 6h7a2.5 2.5 0 0 1 2.5 2.5v7a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-7a2.5 2.5 0 0 1 2.5 -2.5z M10.5 9.5h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 " +
                "0 1 -1 -1v-3a1 1 0 0 1 1 -1z M9.5 3v3 M14.5 3v3 M9.5 18v3 M14.5 18v3 M3 9.5h3 M3 " +
                "14.5h3 M18 9.5h3 M18 14.5h3",
        )
    }

    /** A point with waves on both sides: sensors. */
    val Sensor: ImageVector by lazy {
        dgIcon(
            name = "Sensor",
            strokes = "M8.5 8.5a5 5 0 0 0 0 7 M15.5 8.5a5 5 0 0 1 0 7 M5.6 5.6a9 9 0 0 0 0 12.8 M18.4 5.6a9 " +
                "9 0 0 1 0 12.8",
            dots = "M10.8 12a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
        )
    }

    /** A SIM card. */
    val Sim: ImageVector by lazy {
        dgIcon(
            name = "Sim",
            strokes = "M7.5 3H14l5 5v10.5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18.5v-13A2.5 2.5 0 0 1 " +
                "7.5 3z M10 12h4a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z",
        )
    }

    /** Two linked nodes: a generic connection. */
    val Connection: ImageVector by lazy {
        dgIcon(
            name = "Connection",
            strokes = "M4 17.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z M15 6.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 " +
                "0 -5 0z M8.5 15.5l7-7",
        )
    }

    /** A circled i: information. */
    val Info: ImageVector by lazy {
        dgIcon(
            name = "Info",
            strokes = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M12 11v5.5",
            dots = "M10.9 7.75a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0z",
        )
    }

    /** A light bulb: a tip or suggestion. */
    val Tip: ImageVector by lazy {
        dgIcon(
            name = "Tip",
            strokes = "M9 15.5c0-1.6-3-2.8-3-6.5a6 6 0 0 1 12 0c0 3.7-3 4.9-3 6.5z M9.5 19.5h5",
        )
    }

    /** A trophy cup: an achievement. */
    val Trophy: ImageVector by lazy {
        dgIcon(
            name = "Trophy",
            strokes = "M7 4h10v5a5 5 0 0 1-10 0z M7 6H4v1.5a3 3 0 0 0 3 3 M17 6h3v1.5a3 3 0 0 1-3 3 M12 " +
                "14v6.5 M8.5 20.5h7",
        )
    }

    /** A five-point star: rating or premium. */
    val Star: ImageVector by lazy {
        dgIcon(
            name = "Star",
            strokes = "M12 3.6L14.41 9.28L20.56 9.82L15.9 13.87L17.29 19.88L12 16.7L6.71 19.88L8.1 " +
                "13.87L3.44 9.82L9.59 9.28z",
        )
    }

    /** A target: a goal. */
    val Target: ImageVector by lazy {
        dgIcon(
            name = "Target",
            strokes = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M7.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0z",
            dots = "M10.8 12a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
        )
    }

    /** A calendar page: a date. */
    val Calendar: ImageVector by lazy {
        dgIcon(
            name = "Calendar",
            strokes = "M6.5 5h11a2.5 2.5 0 0 1 2.5 2.5v10.5a2.5 2.5 0 0 1 -2.5 2.5h-11a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-10.5a2.5 2.5 0 0 1 2.5 -2.5z M4 10h16 M8.5 3v4 M15.5 3v4",
        )
    }

    /** A clock face: time or duration. */
    val Clock: ImageVector by lazy {
        dgIcon(
            name = "Clock",
            strokes = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M12 7v5l3.5 2",
        )
    }

    /** A closed padlock. */
    val Lock: ImageVector by lazy {
        dgIcon(
            name = "Lock",
            strokes = "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1 -2.5 2.5h-9a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-5a2.5 2.5 0 0 1 2.5 -2.5z M8 10.5V8a4 4 0 0 1 8 0v2.5",
            dots = "M10.8 15.5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
        )
    }

    /** A globe: internet, region or language. */
    val Globe: ImageVector by lazy {
        dgIcon(
            name = "Globe",
            strokes = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z M3 12h18 M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 " +
                "6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9s1.3-6.4 3.8-9z",
        )
    }

    /** An arrow into a tray: download. */
    val Download: ImageVector by lazy {
        dgIcon(
            name = "Download",
            strokes = "M12 4v11 M7.5 11l4.5 4.5 4.5-4.5 M4.5 16.5v1.5a2.5 2.5 0 0 0 2.5 2.5h10a2.5 2.5 0 0 " +
                "0 2.5-2.5v-1.5",
        )
    }

    /** An arrow out of a tray: upload. */
    val Upload: ImageVector by lazy {
        dgIcon(
            name = "Upload",
            strokes = "M12 15V4 M7.5 8.5L12 4l4.5 4.5 M4.5 16.5v1.5a2.5 2.5 0 0 0 2.5 2.5h10a2.5 2.5 0 0 0 " +
                "2.5-2.5v-1.5",
        )
    }

    /** A line going up: a rising trend. */
    val Chart: ImageVector by lazy {
        dgIcon(
            name = "Chart",
            strokes = "M3.5 17.5l5.5-6 4 4L20.5 8 M15 7.5h5.5V13",
        )
    }

    /** A line going down: a falling trend. */
    val ChartDown: ImageVector by lazy {
        dgIcon(
            name = "ChartDown",
            strokes = "M3.5 6.5l5.5 6 4-4L20.5 16 M15 16.5h5.5V11",
        )
    }

    /** Bars on axes: statistics. */
    val BarChart: ImageVector by lazy {
        dgIcon(
            name = "BarChart",
            strokes = "M4 4v16h16 M9 16v-4 M13.5 16V8 M18 16v-6",
        )
    }

    /** A plain phone: the device. */
    val Phone: ImageVector by lazy {
        dgIcon(
            name = "Phone",
            strokes = "M8.5 3h7a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-13a2.5 2.5 0 0 1 2.5 -2.5z M10.5 17.5h3",
        )
    }

    /** A plain camera. */
    val Camera: ImageVector by lazy {
        dgIcon(
            name = "Camera",
            strokes = "M3 9.5A2.5 2.5 0 0 1 5.5 7h2L9 4.5h6L16.5 7h2A2.5 2.5 0 0 1 21 9.5v8a2.5 2.5 0 0 " +
                "1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z M8.5 13.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z",
        )
    }

    /** A plain microphone. */
    val Mic: ImageVector by lazy {
        dgIcon(
            name = "Mic",
            strokes = "M12 3h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3z M6 11a6 " +
                "6 0 0 0 12 0 M12 17v4 M9 21h6",
        )
    }

    /** A plain battery. */
    val Battery: ImageVector by lazy {
        dgIcon(
            name = "Battery",
            strokes = "M5.5 7h10a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1 -2.5 2.5h-10a2.5 2.5 0 0 1 -2.5 " +
                "-2.5v-5a2.5 2.5 0 0 1 2.5 -2.5z M21 11v2 M6.5 12h5",
        )
    }

    /** A lightning bolt: power or energy. */
    val Bolt: ImageVector by lazy {
        dgIcon(
            name = "Bolt",
            strokes = "M13 3L5 13.5h6.5L11 21l8-10.5h-6.5z",
        )
    }

    /** Four signal bars: signal strength. */
    val Signal: ImageVector by lazy {
        dgIcon(
            name = "Signal",
            strokes = "M4.5 20v-3 M9.5 20v-7 M14.5 20V9 M19.5 20V4",
        )
    }

    /** A heart with a pulse line: device health. */
    val Health: ImageVector by lazy {
        dgIcon(
            name = "Health",
            strokes = "M12 20l-7.4-7.4a4.6 4.6 0 0 1 6.5-6.5l.9 .9 .9-.9a4.6 4.6 0 0 1 6.5 6.5z M8 " +
                "12.5h2l1.2-2 1.8 3.5 1-1.5h2",
        )
    }

    /** A medal on a ribbon: a rank. */
    val Medal: ImageVector by lazy {
        dgIcon(
            name = "Medal",
            strokes = "M7.5 3l4.5 6.5L16.5 3 M6.5 15a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0 -11 0z",
            dots = "M10.8 15a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z",
        )
    }

    /** Every icon by name, in declaration order. For galleries, previews and tests. */
    val all: Map<String, ImageVector> by lazy {
        linkedMapOf(
            "PhoneCheck" to PhoneCheck,
            "CameraCheck" to CameraCheck,
            "MicCheck" to MicCheck,
            "ScreenCheck" to ScreenCheck,
            "TouchCheck" to TouchCheck,
            "BatteryHealth" to BatteryHealth,
            "ChargeFlow" to ChargeFlow,
            "NetworkCheck" to NetworkCheck,
            "Speed" to Speed,
            "Memory" to Memory,
            "Storage" to Storage,
            "Temperature" to Temperature,
            "PrivacyShield" to PrivacyShield,
            "Report" to Report,
            "Leaderboard" to Leaderboard,
            "Streak" to Streak,
            "AiHelp" to AiHelp,
            "AppDoctor" to AppDoctor,
            "ShareScore" to ShareScore,
            "Widget" to Widget,
            "BuySell" to BuySell,
            "Display" to Display,
            "Cpu" to Cpu,
            "Sensor" to Sensor,
            "Sim" to Sim,
            "Connection" to Connection,
            "Info" to Info,
            "Tip" to Tip,
            "Trophy" to Trophy,
            "Star" to Star,
            "Target" to Target,
            "Calendar" to Calendar,
            "Clock" to Clock,
            "Lock" to Lock,
            "Globe" to Globe,
            "Download" to Download,
            "Upload" to Upload,
            "Chart" to Chart,
            "ChartDown" to ChartDown,
            "BarChart" to BarChart,
            "Phone" to Phone,
            "Camera" to Camera,
            "Mic" to Mic,
            "Battery" to Battery,
            "Bolt" to Bolt,
            "Signal" to Signal,
            "Health" to Health,
            "Medal" to Medal,
        )
    }
}
