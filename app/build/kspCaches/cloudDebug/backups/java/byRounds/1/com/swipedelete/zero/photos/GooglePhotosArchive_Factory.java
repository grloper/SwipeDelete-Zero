package com.swipedelete.zero.photos;

import android.content.Context;
import com.swipedelete.zero.data.local.CloudUploadDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class GooglePhotosArchive_Factory implements Factory<GooglePhotosArchive> {
  private final Provider<Context> contextProvider;

  private final Provider<CloudUploadDao> uploadDaoProvider;

  public GooglePhotosArchive_Factory(Provider<Context> contextProvider,
      Provider<CloudUploadDao> uploadDaoProvider) {
    this.contextProvider = contextProvider;
    this.uploadDaoProvider = uploadDaoProvider;
  }

  @Override
  public GooglePhotosArchive get() {
    return newInstance(contextProvider.get(), uploadDaoProvider.get());
  }

  public static GooglePhotosArchive_Factory create(Provider<Context> contextProvider,
      Provider<CloudUploadDao> uploadDaoProvider) {
    return new GooglePhotosArchive_Factory(contextProvider, uploadDaoProvider);
  }

  public static GooglePhotosArchive newInstance(Context context, CloudUploadDao uploadDao) {
    return new GooglePhotosArchive(context, uploadDao);
  }
}
