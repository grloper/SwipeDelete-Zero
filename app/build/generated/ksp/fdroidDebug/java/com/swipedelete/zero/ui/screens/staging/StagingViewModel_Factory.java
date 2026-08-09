package com.swipedelete.zero.ui.screens.staging;

import com.swipedelete.zero.data.repository.PurgeEngine;
import com.swipedelete.zero.data.repository.StagingRepository;
import com.swipedelete.zero.data.repository.StatsStore;
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
public final class StagingViewModel_Factory implements Factory<StagingViewModel> {
  private final Provider<StagingRepository> stagingRepositoryProvider;

  private final Provider<PurgeEngine> purgeEngineProvider;

  private final Provider<StatsStore> statsStoreProvider;

  public StagingViewModel_Factory(Provider<StagingRepository> stagingRepositoryProvider,
      Provider<PurgeEngine> purgeEngineProvider, Provider<StatsStore> statsStoreProvider) {
    this.stagingRepositoryProvider = stagingRepositoryProvider;
    this.purgeEngineProvider = purgeEngineProvider;
    this.statsStoreProvider = statsStoreProvider;
  }

  @Override
  public StagingViewModel get() {
    return newInstance(stagingRepositoryProvider.get(), purgeEngineProvider.get(), statsStoreProvider.get());
  }

  public static StagingViewModel_Factory create(
      Provider<StagingRepository> stagingRepositoryProvider,
      Provider<PurgeEngine> purgeEngineProvider, Provider<StatsStore> statsStoreProvider) {
    return new StagingViewModel_Factory(stagingRepositoryProvider, purgeEngineProvider, statsStoreProvider);
  }

  public static StagingViewModel newInstance(StagingRepository stagingRepository,
      PurgeEngine purgeEngine, StatsStore statsStore) {
    return new StagingViewModel(stagingRepository, purgeEngine, statsStore);
  }
}
