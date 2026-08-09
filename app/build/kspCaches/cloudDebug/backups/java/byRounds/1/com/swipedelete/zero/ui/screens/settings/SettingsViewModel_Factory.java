package com.swipedelete.zero.ui.screens.settings;

import com.swipedelete.zero.data.repository.BackupRepository;
import com.swipedelete.zero.data.repository.ExclusionRepository;
import com.swipedelete.zero.domain.backup.CloudBackup;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<ExclusionRepository> exclusionRepositoryProvider;

  private final Provider<CloudBackup> cloudBackupProvider;

  private final Provider<BackupRepository> backupRepositoryProvider;

  public SettingsViewModel_Factory(Provider<ExclusionRepository> exclusionRepositoryProvider,
      Provider<CloudBackup> cloudBackupProvider,
      Provider<BackupRepository> backupRepositoryProvider) {
    this.exclusionRepositoryProvider = exclusionRepositoryProvider;
    this.cloudBackupProvider = cloudBackupProvider;
    this.backupRepositoryProvider = backupRepositoryProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(exclusionRepositoryProvider.get(), cloudBackupProvider.get(), backupRepositoryProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<ExclusionRepository> exclusionRepositoryProvider,
      Provider<CloudBackup> cloudBackupProvider,
      Provider<BackupRepository> backupRepositoryProvider) {
    return new SettingsViewModel_Factory(exclusionRepositoryProvider, cloudBackupProvider, backupRepositoryProvider);
  }

  public static SettingsViewModel newInstance(ExclusionRepository exclusionRepository,
      CloudBackup cloudBackup, BackupRepository backupRepository) {
    return new SettingsViewModel(exclusionRepository, cloudBackup, backupRepository);
  }
}
