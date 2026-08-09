package com.swipedelete.zero.backup;

import android.content.Context;
import com.swipedelete.zero.data.repository.BackupRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DriveCloudBackup_Factory implements Factory<DriveCloudBackup> {
  private final Provider<Context> contextProvider;

  private final Provider<BackupRepository> backupRepositoryProvider;

  public DriveCloudBackup_Factory(Provider<Context> contextProvider,
      Provider<BackupRepository> backupRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.backupRepositoryProvider = backupRepositoryProvider;
  }

  @Override
  public DriveCloudBackup get() {
    return newInstance(contextProvider.get(), backupRepositoryProvider.get());
  }

  public static DriveCloudBackup_Factory create(Provider<Context> contextProvider,
      Provider<BackupRepository> backupRepositoryProvider) {
    return new DriveCloudBackup_Factory(contextProvider, backupRepositoryProvider);
  }

  public static DriveCloudBackup newInstance(Context context, BackupRepository backupRepository) {
    return new DriveCloudBackup(context, backupRepository);
  }
}
