package com.swipedelete.zero.domain.scanner;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MediaAnalysisWorker_AssistedFactory_Impl implements MediaAnalysisWorker_AssistedFactory {
  private final MediaAnalysisWorker_Factory delegateFactory;

  MediaAnalysisWorker_AssistedFactory_Impl(MediaAnalysisWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public MediaAnalysisWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<MediaAnalysisWorker_AssistedFactory> create(
      MediaAnalysisWorker_Factory delegateFactory) {
    return InstanceFactory.create(new MediaAnalysisWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<MediaAnalysisWorker_AssistedFactory> createFactoryProvider(
      MediaAnalysisWorker_Factory delegateFactory) {
    return InstanceFactory.create(new MediaAnalysisWorker_AssistedFactory_Impl(delegateFactory));
  }
}
