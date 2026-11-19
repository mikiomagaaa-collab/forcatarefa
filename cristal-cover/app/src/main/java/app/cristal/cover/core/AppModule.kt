package app.cristal.cover.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.cristal.cover.data.db.AppPrefDao
import app.cristal.cover.data.db.CristalDatabase
import app.cristal.cover.data.db.HomeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, t -> Logx.e("Erro não tratado no escopo do app", t) }
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + handler)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CristalDatabase =
        Room.databaseBuilder(context, CristalDatabase::class.java, "cristal.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideHomeDao(db: CristalDatabase): HomeDao = db.homeDao()

    @Provides
    fun provideAppPrefDao(db: CristalDatabase): AppPrefDao = db.appPrefDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile("cristal_settings") },
        )

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = true
    }
}
