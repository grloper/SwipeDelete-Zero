package com.swipedelete.zero.data.repository;

import com.swipedelete.zero.data.local.BackedUpFileDao;
import com.swipedelete.zero.data.local.KeptFileDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class BackupRepository_Factory implements Factory<BackupRepository> {
  private final Provider<KeptFileDao> keptFileDaoProvider;

  private final Provider<BackedUpFileDao> backedUpFileDaoProvider;

  public BackupRepository_Factory(Provider<KeptFileDao> keptFileDaoProvider,
      Provider<BackedUpFileDao> backedUpFileDaoProvider) {
    this.keptFileDaoProvider = keptFileDaoProvider;
    this.backedUpFileDaoProvider = backedUpFileDaoProvider;
  }

  @Override
  public BackupRepository get() {
    return newInstance(keptFileDaoProvider.get(), backedUpFileDaoProvider.get());
  }

  public static BackupRepository_Factory create(Provider<KeptFileDao> keptFileDaoProvider,
      Provider<BackedUpFileDao> backedUpFileDaoProvider) {
    return new BackupRepository_Factory(keptFileDaoProvider, backedUpFileDaoProvider);
  }

  public static BackupRepository newInstance(KeptFileDao keptFileDao,
      BackedUpFileDao backedUpFileDao) {
    return new BackupRepository(keptFileDao, backedUpFileDao);
  }
}
