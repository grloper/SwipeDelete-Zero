package com.swipedelete.zero.photos;

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
public final class PhotosUploadWorker_AssistedFactory_Impl implements PhotosUploadWorker_AssistedFactory {
  private final PhotosUploadWorker_Factory delegateFactory;

  PhotosUploadWorker_AssistedFactory_Impl(PhotosUploadWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public PhotosUploadWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<PhotosUploadWorker_AssistedFactory> create(
      PhotosUploadWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PhotosUploadWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<PhotosUploadWorker_AssistedFactory> createFactoryProvider(
      PhotosUploadWorker_Factory delegateFactory) {
    return InstanceFactory.create(new PhotosUploadWorker_AssistedFactory_Impl(delegateFactory));
  }
}
