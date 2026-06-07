package com.hgu.watervalve.di

import android.content.Context
import androidx.room.Room
import com.hgu.watervalve.data.local.db.AppDatabase
import com.hgu.watervalve.data.local.db.DeviceDao
import com.hgu.watervalve.data.local.db.WaterRecordDao
import com.hgu.watervalve.data.remote.api.DeviceSyncApiService
import com.hgu.watervalve.data.remote.api.UwcApiService
import com.hgu.watervalve.data.remote.cookie.SessionCookieJar
import com.hgu.watervalve.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @UwcClient
    fun provideOkHttpClient(cookieJar: SessionCookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    @UwcClient
    fun provideRetrofit(@UwcClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideUwcApiService(@UwcClient retrofit: Retrofit): UwcApiService {
        return retrofit.create(UwcApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "watervalve.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideDeviceDao(db: AppDatabase): DeviceDao = db.deviceDao()

    @Provides
    @Singleton
    fun provideWaterRecordDao(db: AppDatabase): WaterRecordDao = db.waterRecordDao()

    // ── 设备同步云服务 ──

    @Provides
    @Singleton
    @SyncClient
    fun provideSyncOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    @SyncClient
    fun provideSyncRetrofit(@SyncClient syncOkHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.SYNC_SERVER_URL)
            .client(syncOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideDeviceSyncApiService(@SyncClient syncRetrofit: Retrofit): DeviceSyncApiService {
        return syncRetrofit.create(DeviceSyncApiService::class.java)
    }
}
