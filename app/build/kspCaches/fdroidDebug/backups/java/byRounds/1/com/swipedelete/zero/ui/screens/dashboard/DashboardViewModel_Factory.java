package com.swipedelete.zero.ui.screens.dashboard;

import com.swipedelete.zero.data.repository.DeckRepository;
import com.swipedelete.zero.data.repository.StagingRepository;
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<DeckRepository> deckRepositoryProvider;

  private final Provider<StagingRepository> stagingRepositoryProvider;

  public DashboardViewModel_Factory(Provider<DeckRepository> deckRepositoryProvider,
      Provider<StagingRepository> stagingRepositoryProvider) {
    this.deckRepositoryProvider = deckRepositoryProvider;
    this.stagingRepositoryProvider = stagingRepositoryProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(deckRepositoryProvider.get(), stagingRepositoryProvider.get());
  }

  public static DashboardViewModel_Factory create(Provider<DeckRepository> deckRepositoryProvider,
      Provider<StagingRepository> stagingRepositoryProvider) {
    return new DashboardViewModel_Factory(deckRepositoryProvider, stagingRepositoryProvider);
  }

  public static DashboardViewModel newInstance(DeckRepository deckRepository,
      StagingRepository stagingRepository) {
    return new DashboardViewModel(deckRepository, stagingRepository);
  }
}
