package com.moneta.app.core.di

import com.moneta.app.data.backup.CloudBackupProvider
import com.moneta.app.data.backup.UnavailableCloudBackupProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The only place that decides which [CloudBackupProvider] implementation the
 * app uses. When a real Google Drive-backed provider is built (Section 12),
 * swapping the binding here is the entire integration point - no other
 * cloud-backup code changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBackupModule {

    @Binds
    @Singleton
    abstract fun bindCloudBackupProvider(impl: UnavailableCloudBackupProvider): CloudBackupProvider
}
