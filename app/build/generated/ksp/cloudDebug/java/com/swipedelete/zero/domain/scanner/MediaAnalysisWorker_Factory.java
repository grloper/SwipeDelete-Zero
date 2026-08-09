package com.swipedelete.zero.domain.scanner;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.swipedelete.zero.data.local.MediaAnalysisDao;
import com.swipedelete.zero.data.repository.MediaStoreRepository;
import dagger.internal.DaggerGenerated;
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
public final class MediaAnalysisWorker_Factory {
  private final Provider<MediaStoreRepository> mediaStoreProvider;

  private final Provider<MediaAnalysisDao> analysisDaoProvider;

  private final Provider<VideoMetadataExtractor> videoMetadataExtractorProvider;

  public MediaAnalysisWorker_Factory(Provider<MediaStoreRepository> mediaStoreProvider,
      Provider<MediaAnalysisDao> analysisDaoProvider,
      Provider<VideoMetadataExtractor> videoMetadataExtractorProvider) {
    this.mediaStoreProvider = mediaStoreProvider;
    this.analysisDaoProvider = analysisDaoProvider;
    this.videoMetadataExtractorProvider = videoMetadataExtractorProvider;
  }

  public MediaAnalysisWorker get(Context appContext, WorkerParameters params) {
    return newInstance(appContext, params, mediaStoreProvider.get(), analysisDaoProvider.get(), videoMetadataExtractorProvider.get());
  }

  public static MediaAnalysisWorker_Factory create(
      Provider<MediaStoreRepository> mediaStoreProvider,
      Provider<MediaAnalysisDao> analysisDaoProvider,
      Provider<VideoMetadataExtractor> videoMetadataExtractorProvider) {
    return new MediaAnalysisWorker_Factory(mediaStoreProvider, analysisDaoProvider, videoMetadataExtractorProvider);
  }

  public static MediaAnalysisWorker newInstance(Context appContext, WorkerParameters params,
      MediaStoreRepository mediaStore, MediaAnalysisDao analysisDao,
      VideoMetadataExtractor videoMetadataExtractor) {
    return new MediaAnalysisWorker(appContext, params, mediaStore, analysisDao, videoMetadataExtractor);
  }
}
