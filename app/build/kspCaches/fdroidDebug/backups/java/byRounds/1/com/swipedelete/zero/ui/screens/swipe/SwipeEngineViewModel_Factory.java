package com.swipedelete.zero.ui.screens.swipe;

import androidx.lifecycle.SavedStateHandle;
import com.swipedelete.zero.data.local.MediaAnalysisDao;
import com.swipedelete.zero.data.repository.BackupRepository;
import com.swipedelete.zero.data.repository.DeckRepository;
import com.swipedelete.zero.data.repository.ExclusionRepository;
import com.swipedelete.zero.data.repository.MediaPreloader;
import com.swipedelete.zero.data.repository.StagingRepository;
import com.swipedelete.zero.data.repository.StatsStore;
import com.swipedelete.zero.domain.backup.PhotosArchive;
import com.swipedelete.zero.domain.scanner.VideoMetadataExtractor;
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
public final class SwipeEngineViewModel_Factory implements Factory<SwipeEngineViewModel> {
  private final Provider<DeckRepository> deckRepositoryProvider;

  private final Provider<StagingRepository> stagingRepositoryProvider;

  private final Provider<ExclusionRepository> exclusionRepositoryProvider;

  private final Provider<BackupRepository> backupRepositoryProvider;

  private final Provider<PhotosArchive> photosArchiveProvider;

  private final Provider<VideoMetadataExtractor> videoMetadataExtractorProvider;

  private final Provider<MediaAnalysisDao> analysisDaoProvider;

  private final Provider<MediaPreloader> mediaPreloaderProvider;

  private final Provider<StatsStore> statsStoreProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public SwipeEngineViewModel_Factory(Provider<DeckRepository> deckRepositoryProvider,
      Provider<StagingRepository> stagingRepositoryProvider,
      Provider<ExclusionRepository> exclusionRepositoryProvider,
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<PhotosArchive> photosArchiveProvider,
      Provider<VideoMetadataExtractor> videoMetadataExtractorProvider,
      Provider<MediaAnalysisDao> analysisDaoProvider,
      Provider<MediaPreloader> mediaPreloaderProvider, Provider<StatsStore> statsStoreProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.deckRepositoryProvider = deckRepositoryProvider;
    this.stagingRepositoryProvider = stagingRepositoryProvider;
    this.exclusionRepositoryProvider = exclusionRepositoryProvider;
    this.backupRepositoryProvider = backupRepositoryProvider;
    this.photosArchiveProvider = photosArchiveProvider;
    this.videoMetadataExtractorProvider = videoMetadataExtractorProvider;
    this.analysisDaoProvider = analysisDaoProvider;
    this.mediaPreloaderProvider = mediaPreloaderProvider;
    this.statsStoreProvider = statsStoreProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public SwipeEngineViewModel get() {
    return newInstance(deckRepositoryProvider.get(), stagingRepositoryProvider.get(), exclusionRepositoryProvider.get(), backupRepositoryProvider.get(), photosArchiveProvider.get(), videoMetadataExtractorProvider.get(), analysisDaoProvider.get(), mediaPreloaderProvider.get(), statsStoreProvider.get(), savedStateHandleProvider.get());
  }

  public static SwipeEngineViewModel_Factory create(Provider<DeckRepository> deckRepositoryProvider,
      Provider<StagingRepository> stagingRepositoryProvider,
      Provider<ExclusionRepository> exclusionRepositoryProvider,
      Provider<BackupRepository> backupRepositoryProvider,
      Provider<PhotosArchive> photosArchiveProvider,
      Provider<VideoMetadataExtractor> videoMetadataExtractorProvider,
      Provider<MediaAnalysisDao> analysisDaoProvider,
      Provider<MediaPreloader> mediaPreloaderProvider, Provider<StatsStore> statsStoreProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new SwipeEngineViewModel_Factory(deckRepositoryProvider, stagingRepositoryProvider, exclusionRepositoryProvider, backupRepositoryProvider, photosArchiveProvider, videoMetadataExtractorProvider, analysisDaoProvider, mediaPreloaderProvider, statsStoreProvider, savedStateHandleProvider);
  }

  public static SwipeEngineViewModel newInstance(DeckRepository deckRepository,
      StagingRepository stagingRepository, ExclusionRepository exclusionRepository,
      BackupRepository backupRepository, PhotosArchive photosArchive,
      VideoMetadataExtractor videoMetadataExtractor, MediaAnalysisDao analysisDao,
      MediaPreloader mediaPreloader, StatsStore statsStore, SavedStateHandle savedStateHandle) {
    return new SwipeEngineViewModel(deckRepository, stagingRepository, exclusionRepository, backupRepository, photosArchive, videoMetadataExtractor, analysisDao, mediaPreloader, statsStore, savedStateHandle);
  }
}
