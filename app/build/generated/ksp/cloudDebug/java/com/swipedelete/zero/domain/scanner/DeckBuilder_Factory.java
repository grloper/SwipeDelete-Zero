package com.swipedelete.zero.domain.scanner;

import com.swipedelete.zero.data.local.ExclusionDao;
import com.swipedelete.zero.data.local.MediaAnalysisDao;
import com.swipedelete.zero.data.repository.MediaStoreRepository;
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
public final class DeckBuilder_Factory implements Factory<DeckBuilder> {
  private final Provider<MediaStoreRepository> mediaStoreProvider;

  private final Provider<MediaAnalysisDao> analysisDaoProvider;

  private final Provider<ExclusionDao> exclusionDaoProvider;

  public DeckBuilder_Factory(Provider<MediaStoreRepository> mediaStoreProvider,
      Provider<MediaAnalysisDao> analysisDaoProvider, Provider<ExclusionDao> exclusionDaoProvider) {
    this.mediaStoreProvider = mediaStoreProvider;
    this.analysisDaoProvider = analysisDaoProvider;
    this.exclusionDaoProvider = exclusionDaoProvider;
  }

  @Override
  public DeckBuilder get() {
    return newInstance(mediaStoreProvider.get(), analysisDaoProvider.get(), exclusionDaoProvider.get());
  }

  public static DeckBuilder_Factory create(Provider<MediaStoreRepository> mediaStoreProvider,
      Provider<MediaAnalysisDao> analysisDaoProvider, Provider<ExclusionDao> exclusionDaoProvider) {
    return new DeckBuilder_Factory(mediaStoreProvider, analysisDaoProvider, exclusionDaoProvider);
  }

  public static DeckBuilder newInstance(MediaStoreRepository mediaStore,
      MediaAnalysisDao analysisDao, ExclusionDao exclusionDao) {
    return new DeckBuilder(mediaStore, analysisDao, exclusionDao);
  }
}
