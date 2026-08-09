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
public final class StoragePermissionManager_Factory implements Factory<StoragePermissionManager> {
  private final Provider<Context> contextProvider;

  public StoragePermissionManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public StoragePermissionManager get() {
    return newInstance(contextProvider.get());
  }

  public static StoragePermissionManager_Factory create(Provider<Context> contextProvider) {
    return new StoragePermissionManager_Factory(contextProvider);
  }

  public static StoragePermissionManager newInstance(Context context) {
    return new StoragePermissionManager(context);
  }
}
