package com.zoomers.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

/**
 * Coil reads this factory app-wide, so every AsyncImage gets a smooth crossfade
 * fade-in plus memory + disk caching (snappier scrolling, images survive config
 * changes and app restarts).
 */
@HiltAndroidApp
class ZoomersApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(220)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .build()
}
