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
public final class NoOpCloudBackup_Factory implements Factory<NoOpCloudBackup> {
  @Override
  public NoOpCloudBackup get() {
    return newInstance();
  }

  public static NoOpCloudBackup_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NoOpCloudBackup newInstance() {
    return new NoOpCloudBackup();
  }

  private static final class InstanceHolder {
    private static final NoOpCloudBackup_Factory INSTANCE = new NoOpCloudBackup_Factory();
  }
}
