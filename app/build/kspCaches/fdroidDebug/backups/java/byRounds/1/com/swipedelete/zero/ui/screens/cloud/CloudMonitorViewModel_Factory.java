package com.swipedelete.zero.ui.screens.cloud;

import com.swipedelete.zero.data.local.CloudUploadDao;
import com.swipedelete.zero.data.repository.BackupRepository;
import com.swipedelete.zero.domain.backup.CloudBackup;
import com.swipedelete.zero.domain.backup.PhotosArchive;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class CloudMonitorViewModel_Factory implements Factory<CloudMonitorViewModel> {
  private final Provider<BackupRepository> backupRepositoryProvider;

  private final Provider<CloudBackup> cloudBackupProvider;

  private final Provider<PhotosArchive> photosArchiveProvider;

  private final Provider<CloudUploadDao> uploadDaoProvider;

  public CloudMonitorViewModel_Factory(Provider<BackupRepository> backupRepositoryProvider,
      Provider<CloudBackup> cloudBackupProvider, Provider<PhotosArchive> photosArchiveProvider,
      Provider<CloudUploadDao> uploadDaoProvider) {
    this.backupRepositoryProvider = backupRepositoryProvider;
    this.cloudBackupProvider = cloudBackupProvider;
    this.photosArchiveProvider = photosArchiveProvider;
    this.uploadDaoProvider = uploadDaoProvider;
  }

  @Override
  public CloudMonitorViewModel get() {
    return newInstance(backupRepositoryProvider.get(), cloudBackupProvider.get(), photosArchiveProvider.get(), uploadDaoProvider.get());
  }

  public static CloudMonitorViewModel_Factory create(
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<CloudBackup> cloudBackupProvider, Provider<PhotosArchive> photosArchiveProvider,
      Provider<CloudUploadDao> uploadDaoProvider) {
    return new CloudMonitorViewModel_Factory(backupRepositoryProvider, cloudBackupProvider, photosArchiveProvider, uploadDaoProvider);
  }

  public static CloudMonitorViewModel newInstance(BackupRepository backupRepository,
      CloudBackup cloudBackup, PhotosArchive photosArchive, CloudUploadDao uploadDao) {
    return new CloudMonitorViewModel(backupRepository, cloudBackup, photosArchive, uploadDao);
  }
}
