package com.swipedelete.zero.di

import com.swipedelete.zero.backup.DriveCloudBackup
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.photos.GooglePhotosArchive
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Cloud flavor: real Google Drive backup + Google Photos swipe-up archive. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudBackupModule {
    @Binds
    abstract fun bindCloudBackup(impl: DriveCloudBackup): CloudBackup

    @Binds
    abstract fun bindPhotosArchive(impl: GooglePhotosArchive): PhotosArchive
}
