package com.swipedelete.zero;

import androidx.hilt.work.HiltWorkerFactory;
import com.swipedelete.zero.domain.scanner.AnalysisScheduler;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class SwipeDeleteApplication_MembersInjector implements MembersInjector<SwipeDeleteApplication> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private final Provider<AnalysisScheduler> analysisSchedulerProvider;

  public SwipeDeleteApplication_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<AnalysisScheduler> analysisSchedulerProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
    this.analysisSchedulerProvider = analysisSchedulerProvider;
  }

  public static MembersInjector<SwipeDeleteApplication> create(
      Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<AnalysisScheduler> analysisSchedulerProvider) {
    return new SwipeDeleteApplication_MembersInjector(workerFactoryProvider, analysisSchedulerProvider);
  }

  @Override
  public void injectMembers(SwipeDeleteApplication instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
    injectAnalysisScheduler(instance, analysisSchedulerProvider.get());
  }

  @InjectedFieldSignature("com.swipedelete.zero.SwipeDeleteApplication.workerFactory")
  public static void injectWorkerFactory(SwipeDeleteApplication instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }

  @InjectedFieldSignature("com.swipedelete.zero.SwipeDeleteApplication.analysisScheduler")
  public static void injectAnalysisScheduler(SwipeDeleteApplication instance,
      AnalysisScheduler analysisScheduler) {
    instance.analysisScheduler = analysisScheduler;
  }
}
