package dev.skiff

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The few Material icons the core set lacks (24 dp, Material Symbols paths), instead of the whole extended set. */
object AppIcons {
    val Tv: ImageVector = icon(
        "Tv",
        "M21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z",
    )
    val Movie: ImageVector = icon(
        "Movie",
        "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z",
    )
    val Series: ImageVector = icon(
        "Series",
        "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8C6.9,2 6,2.9 6,4v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,14.5v-9l6,4.5 -6,4.5z",
    )

    val Bookmark: ImageVector = icon(
        "Bookmark",
        "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z",
    )
    val BookmarkBorder: ImageVector = icon(
        "BookmarkBorder",
        "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z",
    )
    val History: ImageVector = icon(
        "History",
        "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z",
    )

    // The remote
    val Remote: ImageVector = icon(
        "Remote",
        "M15,9H9c-0.55,0 -1,0.45 -1,1v12c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1V10c0,-0.55 -0.45,-1 -1,-1zM12,15c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM7.05,6.05l1.41,1.41C9.37,6.56 10.62,6 12,6s2.63,0.56 3.54,1.46l1.41,-1.41C15.68,4.78 13.93,4 12,4s-3.68,0.78 -4.95,2.05zM12,0C8.96,0 6.21,1.23 4.22,3.22l1.41,1.41C7.26,3.01 9.51,2 12,2s4.74,1.01 6.36,2.64l1.41,-1.41C17.79,1.23 15.04,0 12,0z",
    )
    val VolumeUp: ImageVector = icon(
        "VolumeUp",
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    )
    val VolumeDown: ImageVector = icon(
        "VolumeDown",
        "M18.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM5,9v6h4l5,5V4L9,9H5z",
    )
    val VolumeOff: ImageVector = icon(
        "VolumeOff",
        "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z",
    )
    val FastForward: ImageVector = icon(
        "FastForward",
        "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z",
    )
    val FastRewind: ImageVector = icon(
        "FastRewind",
        "M11,18V6l-8.5,6 8.5,6zM11.5,12l8.5,6V6l-8.5,6z",
    )
    val PlayPause: ImageVector = icon(
        "PlayPause",
        "M2,6v12l8.5,-6L2,6zM13,6h3v12h-3zM18,6h3v12h-3z",
    )
    val Power: ImageVector = icon(
        "Power",
        "M13,3h-2v10h2L13,3zM17.83,5.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z",
    )
    val Keyboard: ImageVector = icon(
        "Keyboard",
        "M20,5H4c-1.1,0 -1.99,0.9 -1.99,2L2,17c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM11,8h2v2h-2V8zM11,11h2v2h-2v-2zM8,8h2v2H8V8zM8,11h2v2H8v-2zM7,13H5v-2h2v2zM7,10H5V8h2v2zM16,17H8v-2h8v2zM16,13h-2v-2h2v2zM16,10h-2V8h2v2zM19,13h-2v-2h2v2zM19,10h-2V8h2v2z",
    )
    val Mouse: ImageVector = icon(
        "Mouse",
        "M13,1.07V9h7c0,-4.08 -3.05,-7.44 -7,-7.93zM4,15c0,4.42 3.58,8 8,8s8,-3.58 8,-8v-4H4v4zM11,1.07C7.05,1.56 4,4.92 4,9h7V1.07z",
    )

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
