package com.swipedelete.zero.di

import com.swipedelete.zero.backup.DriveCloudBackup
import com.swipedelete.zero.domain.backup.CloudBackup
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
}
