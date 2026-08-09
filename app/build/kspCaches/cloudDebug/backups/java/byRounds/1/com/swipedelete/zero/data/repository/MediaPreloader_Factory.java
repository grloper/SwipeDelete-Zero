package com.swipedelete.zero.data.repository;

import android.content.Context;
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
public final class MediaPreloader_Factory implements Factory<MediaPreloader> {
  private final Provider<Context> contextProvider;

  public MediaPreloader_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MediaPreloader get() {
    return newInstance(contextProvider.get());
  }

  public static MediaPreloader_Factory create(Provider<Context> contextProvider) {
    return new MediaPreloader_Factory(contextProvider);
  }

  public static MediaPreloader newInstance(Context context) {
    return new MediaPreloader(context);
  }
}
