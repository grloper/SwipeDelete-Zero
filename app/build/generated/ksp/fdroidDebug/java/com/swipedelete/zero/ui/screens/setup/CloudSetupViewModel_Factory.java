package com.swipedelete.zero.ui.screens.setup;

import com.swipedelete.zero.domain.backup.CloudBackup;
import com.swipedelete.zero.domain.setup.SigningIdentityReader;
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
public final class CloudSetupViewModel_Factory implements Factory<CloudSetupViewModel> {
  private final Provider<CloudBackup> cloudBackupProvider;

  private final Provider<SigningIdentityReader> signingIdentityReaderProvider;

  public CloudSetupViewModel_Factory(Provider<CloudBackup> cloudBackupProvider,
      Provider<SigningIdentityReader> signingIdentityReaderProvider) {
    this.cloudBackupProvider = cloudBackupProvider;
    this.signingIdentityReaderProvider = signingIdentityReaderProvider;
  }

  @Override
  public CloudSetupViewModel get() {
    return newInstance(cloudBackupProvider.get(), signingIdentityReaderProvider.get());
  }

  public static CloudSetupViewModel_Factory create(Provider<CloudBackup> cloudBackupProvider,
      Provider<SigningIdentityReader> signingIdentityReaderProvider) {
    return new CloudSetupViewModel_Factory(cloudBackupProvider, signingIdentityReaderProvider);
  }

  public static CloudSetupViewModel newInstance(CloudBackup cloudBackup,
      SigningIdentityReader signingIdentityReader) {
    return new CloudSetupViewModel(cloudBackup, signingIdentityReader);
  }
}
