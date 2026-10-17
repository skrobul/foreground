package me.bgregos.foreground.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import me.bgregos.foreground.network.RemoteTaskChampionSourceImpl
import me.bgregos.foreground.network.RemoteTaskSource
import me.bgregos.foreground.network.RemoteTaskChampionSource
import me.bgregos.foreground.network.RemoteTaskSourceImpl
import me.bgregos.foreground.data.tasks.TaskRepository
import java.io.File
import javax.inject.Singleton

@Module
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("foreground_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideRemoteTaskSource(
        context: Context,
        prefs: SharedPreferences
    ): RemoteTaskSource {
        val filesDir = context.filesDir
        // For now, defaulting to taskd implementation
        return RemoteTaskSourceImpl(filesDir, prefs)
    }

    @Provides
    @Singleton
    fun provideRemoteTaskChampionSource(
        context: Context,
        prefs: SharedPreferences
    ): RemoteTaskChampionSource {
        val filesDir = context.filesDir
        return RemoteTaskChampionSourceImpl(filesDir, prefs)
    }

    @Provides
    @Singleton
    fun provideTaskRepository(
        prefs: SharedPreferences,
        remoteTaskSource: RemoteTaskSource
    ): TaskRepository {
        return TaskRepository(prefs, remoteTaskSource)
    }
}
