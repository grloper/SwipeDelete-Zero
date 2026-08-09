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
public final class PurgeEngine_Factory implements Factory<PurgeEngine> {
  private final Provider<Context> contextProvider;

  private final Provider<MediaStoreRepository> mediaStoreProvider;

  private final Provider<SafStorageBridge> safBridgeProvider;

  private final Provider<StoragePermissionManager> permissionsProvider;

  public PurgeEngine_Factory(Provider<Context> contextProvider,
      Provider<MediaStoreRepository> mediaStoreProvider,
      Provider<SafStorageBridge> safBridgeProvider,
      Provider<StoragePermissionManager> permissionsProvider) {
    this.contextProvider = contextProvider;
    this.mediaStoreProvider = mediaStoreProvider;
    this.safBridgeProvider = safBridgeProvider;
    this.permissionsProvider = permissionsProvider;
  }

  @Override
  public PurgeEngine get() {
    return newInstance(contextProvider.get(), mediaStoreProvider.get(), safBridgeProvider.get(), permissionsProvider.get());
  }

  public static PurgeEngine_Factory create(Provider<Context> contextProvider,
      Provider<MediaStoreRepository> mediaStoreProvider,
      Provider<SafStorageBridge> safBridgeProvider,
      Provider<StoragePermissionManager> permissionsProvider) {
    return new PurgeEngine_Factory(contextProvider, mediaStoreProvider, safBridgeProvider, permissionsProvider);
  }

  public static PurgeEngine newInstance(Context context, MediaStoreRepository mediaStore,
      SafStorageBridge safBridge, StoragePermissionManager permissions) {
    return new PurgeEngine(context, mediaStore, safBridge, permissions);
  }
}
