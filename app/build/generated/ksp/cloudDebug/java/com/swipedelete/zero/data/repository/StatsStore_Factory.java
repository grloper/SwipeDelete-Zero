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
public final class StatsStore_Factory implements Factory<StatsStore> {
  private final Provider<Context> contextProvider;

  public StatsStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public StatsStore get() {
    return newInstance(contextProvider.get());
  }

  public static StatsStore_Factory create(Provider<Context> contextProvider) {
    return new StatsStore_Factory(contextProvider);
  }

  public static StatsStore newInstance(Context context) {
    return new StatsStore(context);
  }
}
