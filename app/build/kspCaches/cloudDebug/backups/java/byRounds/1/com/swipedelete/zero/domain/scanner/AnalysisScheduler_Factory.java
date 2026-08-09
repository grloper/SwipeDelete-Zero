package com.swipedelete.zero.domain.scanner;

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
public final class AnalysisScheduler_Factory implements Factory<AnalysisScheduler> {
  private final Provider<Context> contextProvider;

  public AnalysisScheduler_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AnalysisScheduler get() {
    return newInstance(contextProvider.get());
  }

  public static AnalysisScheduler_Factory create(Provider<Context> contextProvider) {
    return new AnalysisScheduler_Factory(contextProvider);
  }

  public static AnalysisScheduler newInstance(Context context) {
    return new AnalysisScheduler(context);
  }
}
