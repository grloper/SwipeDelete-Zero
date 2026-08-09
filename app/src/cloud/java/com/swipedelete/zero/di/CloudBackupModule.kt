package com.swipedelete.zero.di

import com.swipedelete.zero.backup.DriveCloudBackup
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.backup.PhotosArchive
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Cloud flavor: bind the real Google Drive backup engine. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBackupModule {
    @Binds
    abstract fun bindCloudBackup(impl: DriveCloudBackup): CloudBackup

    // Replaced by GooglePhotosArchive in the Photos-archive phase.
    @Binds
    abstract fun bindPhotosArchive(impl: NoOpPhotosArchive): PhotosArchive
}
