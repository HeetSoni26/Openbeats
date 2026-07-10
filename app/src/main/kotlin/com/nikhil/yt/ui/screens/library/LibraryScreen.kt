/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */



package com.nikhil.yt.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.nikhil.yt.LocalDatabase
import com.nikhil.yt.R
import com.nikhil.yt.constants.ChipSortTypeKey
import com.nikhil.yt.constants.DisableBlurKey
import com.nikhil.yt.constants.LibraryFilter
import com.nikhil.yt.constants.PlaylistTagsFilterKey
import com.nikhil.yt.constants.ShowTagsInLibraryKey
import com.nikhil.yt.ui.component.ChipsRow
import com.nikhil.yt.ui.component.TagsFilterChips
import com.nikhil.yt.utils.rememberEnumPreference
import com.nikhil.yt.utils.rememberPreference

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val (disableBlur) = rememberPreference(DisableBlurKey, true)

    val database = LocalDatabase.current
    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, true)
    val (selectedTagsFilter, onSelectedTagsFilterChange) = rememberPreference(PlaylistTagsFilterKey, "")
    val selectedTagIds = remember(selectedTagsFilter) {
        selectedTagsFilter.split(",").filter { it.isNotBlank() }.toSet()
    }

    val filterContent = @Composable {
        Column {
            Row {
                ChipsRow(
                    chips =
                    listOf(
                        LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                        LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                        LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                        LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    ),
                    currentValue = filterType,
                    onValueUpdate = {
                        filterType =
                            if (filterType == it) {
                                LibraryFilter.LIBRARY
                            } else {
                                it
                            }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (showTagsInLibrary) {
                TagsFilterChips(
                    database = database,
                    selectedTags = selectedTagIds,
                    onTagToggle = { tag ->
                        val newTags = if (tag.id in selectedTagIds) {
                            selectedTagIds - tag.id
                        } else {
                            selectedTagIds + tag.id
                        }
                        onSelectedTagsFilterChange(newTags.joinToString(","))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Openbeats glassmorphism palette
    val glassCyan = com.nikhil.yt.ui.component.GlassPalette.Cyan
    val glassViolet = com.nikhil.yt.ui.component.GlassPalette.Violet
    val glassPink = com.nikhil.yt.ui.component.GlassPalette.Pink
    val glassCyanDim = com.nikhil.yt.ui.component.GlassPalette.CyanDim
    val glassVioletDim = com.nikhil.yt.ui.component.GlassPalette.VioletDim
    val glassBase = com.nikhil.yt.ui.component.GlassPalette.BackgroundDeep

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Openbeats glassmorphism mesh gradient background
        if (!disableBlur) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f)
                .drawBehind {
                    val width = size.width
                    val height = size.height
                    
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glassCyan.copy(alpha = 0.30f),
                                glassCyan.copy(alpha = 0.15f),
                                glassCyan.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.12f, height * 0.08f),
                            radius = width * 0.55f
                        )
                    )
                    
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glassViolet.copy(alpha = 0.28f),
                                glassViolet.copy(alpha = 0.14f),
                                glassViolet.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.88f, height * 0.15f),
                            radius = width * 0.60f
                        )
                    )
                    
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glassPink.copy(alpha = 0.22f),
                                glassPink.copy(alpha = 0.10f),
                                glassPink.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.25f, height * 0.42f),
                            radius = width * 0.55f
                        )
                    )
                    
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glassCyanDim.copy(alpha = 0.18f),
                                glassCyanDim.copy(alpha = 0.08f),
                                glassCyanDim.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.75f, height * 0.55f),
                            radius = width * 0.65f
                        )
                    )
                    
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glassVioletDim.copy(alpha = 0.15f),
                                glassVioletDim.copy(alpha = 0.07f),
                                glassVioletDim.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.50f, height * 0.80f),
                            radius = width * 0.75f
                        )
                    )
                    
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                glassBase.copy(alpha = 0.30f),
                                glassBase.copy(alpha = 0.65f),
                                glassBase
                            ),
                            startY = height * 0.35f,
                            endY = height
                        )
                    )
                }
        ) {}
        }

        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(navController, filterContent)
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })
        }
    }
}
