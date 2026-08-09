package com.swipedelete.zero.di

import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.NoOpCloudBackup
import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.backup.PhotosArchive
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Play flavor: air-gapped — cloud backup & Photos archive are compile-time no-ops. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBackupModule {
    @Binds
    abstract fun bindCloudBackup(impl: NoOpCloudBackup): CloudBackup

    @Binds
    abstract fun bindPhotosArchive(impl: NoOpPhotosArchive): PhotosArchive
}
