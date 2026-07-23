package com.swipedelete.zero.di

import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.NoOpCloudBackup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Play flavor: air-gapped — cloud backup is a compile-time no-op. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBackupModule {
    @Binds
    abstract fun bindCloudBackup(impl: NoOpCloudBackup): CloudBackup
}
