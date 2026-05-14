package com.ascify.app.di

import android.content.Context
import com.ascify.app.camera.CameraController
import com.ascify.app.export.ExportEngine
import com.ascify.app.renderer.ASCIIRenderer
import com.ascify.app.renderer.FrameAnalyzer
import com.ascify.app.settings.SettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideASCIIRenderer(): ASCIIRenderer = ASCIIRenderer()

    @Provides
    @Singleton
    fun provideFrameAnalyzer(renderer: ASCIIRenderer): FrameAnalyzer = FrameAnalyzer(renderer)

    @Provides
    @Singleton
    fun provideCameraController(
        @ApplicationContext context: Context,
        frameAnalyzer: FrameAnalyzer
    ): CameraController = CameraController(context, frameAnalyzer)

    @Provides
    @Singleton
    fun provideExportEngine(
        @ApplicationContext context: Context,
        renderer: ASCIIRenderer
    ): ExportEngine = ExportEngine(context, renderer)

    @Provides
    @Singleton
    fun provideSettingsManager(
        @ApplicationContext context: Context
    ): SettingsManager = SettingsManager(context)
}
