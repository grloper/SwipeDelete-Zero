package com.swipedelete.zero.domain.backup;

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
public final class NoOpPhotosArchive_Factory implements Factory<NoOpPhotosArchive> {
  @Override
  public NoOpPhotosArchive get() {
    return newInstance();
  }

  public static NoOpPhotosArchive_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NoOpPhotosArchive newInstance() {
    return new NoOpPhotosArchive();
  }

  private static final class InstanceHolder {
    private static final NoOpPhotosArchive_Factory INSTANCE = new NoOpPhotosArchive_Factory();
  }
}
