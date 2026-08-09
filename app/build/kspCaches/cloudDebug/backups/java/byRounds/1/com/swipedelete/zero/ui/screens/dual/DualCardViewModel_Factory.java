package com.swipedelete.zero.ui.screens.dual;

import androidx.lifecycle.SavedStateHandle;
import com.swipedelete.zero.data.repository.BackupRepository;
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
public final class DualCardViewModel_Factory implements Factory<DualCardViewModel> {
  private final Provider<DeckRepository> deckRepositoryProvider;

  private final Provider<StagingRepository> stagingRepositoryProvider;

  private final Provider<BackupRepository> backupRepositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public DualCardViewModel_Factory(Provider<DeckRepository> deckRepositoryProvider,
      Provider<StagingRepository> stagingRepositoryProvider,
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.deckRepositoryProvider = deckRepositoryProvider;
    this.stagingRepositoryProvider = stagingRepositoryProvider;
    this.backupRepositoryProvider = backupRepositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public DualCardViewModel get() {
    return newInstance(deckRepositoryProvider.get(), stagingRepositoryProvider.get(), backupRepositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static DualCardViewModel_Factory create(Provider<DeckRepository> deckRepositoryProvider,
      Provider<StagingRepository> stagingRepositoryProvider,
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new DualCardViewModel_Factory(deckRepositoryProvider, stagingRepositoryProvider, backupRepositoryProvider, savedStateHandleProvider);
  }

  public static DualCardViewModel newInstance(DeckRepository deckRepository,
      StagingRepository stagingRepository, BackupRepository backupRepository,
      SavedStateHandle savedStateHandle) {
    return new DualCardViewModel(deckRepository, stagingRepository, backupRepository, savedStateHandle);
  }
}
