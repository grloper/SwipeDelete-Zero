package com.swipedelete.zero.photos;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class PhotosUploader_Factory implements Factory<PhotosUploader> {
  @Override
  public PhotosUploader get() {
    return newInstance();
  }

  public static PhotosUploader_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PhotosUploader newInstance() {
    return new PhotosUploader();
  }

  private static final class InstanceHolder {
    private static final PhotosUploader_Factory INSTANCE = new PhotosUploader_Factory();
  }
}
