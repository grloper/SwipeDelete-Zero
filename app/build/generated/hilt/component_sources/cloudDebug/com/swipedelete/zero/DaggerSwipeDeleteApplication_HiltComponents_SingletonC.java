package com.swipedelete.zero;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.swipedelete.zero.backup.DriveCloudBackup;
import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.BackedUpFileDao;
import com.swipedelete.zero.data.local.CloudUploadDao;
import com.swipedelete.zero.data.local.DeckSessionDao;
import com.swipedelete.zero.data.local.ExclusionDao;
import com.swipedelete.zero.data.local.KeptFileDao;
import com.swipedelete.zero.data.local.MediaAnalysisDao;
import com.swipedelete.zero.data.local.StagedFileDao;
import com.swipedelete.zero.data.repository.BackupRepository;
import com.swipedelete.zero.data.repository.DeckRepository;
import com.swipedelete.zero.data.repository.ExclusionRepository;
import com.swipedelete.zero.data.repository.MediaPreloader;
import com.swipedelete.zero.data.repository.MediaStoreRepository;
import com.swipedelete.zero.data.repository.PurgeEngine;
import com.swipedelete.zero.data.repository.SafStorageBridge;
import com.swipedelete.zero.data.repository.StagingRepository;
import com.swipedelete.zero.data.repository.StatsStore;
import com.swipedelete.zero.data.repository.StoragePermissionManager;
import com.swipedelete.zero.di.DatabaseModule_ProvideBackedUpFileDaoFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideCloudUploadDaoFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideDatabaseFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideDeckSessionDaoFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideExclusionDaoFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideKeptFileDaoFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideMediaAnalysisDaoFactory;
import com.swipedelete.zero.di.DatabaseModule_ProvideStagedFileDaoFactory;
import com.swipedelete.zero.domain.scanner.AnalysisScheduler;
import com.swipedelete.zero.domain.scanner.DeckBuilder;
import com.swipedelete.zero.domain.scanner.MediaAnalysisWorker;
import com.swipedelete.zero.domain.scanner.MediaAnalysisWorker_AssistedFactory;
import com.swipedelete.zero.domain.scanner.VideoMetadataExtractor;
import com.swipedelete.zero.photos.GooglePhotosArchive;
import com.swipedelete.zero.photos.PhotosUploadWorker;
import com.swipedelete.zero.photos.PhotosUploadWorker_AssistedFactory;
import com.swipedelete.zero.photos.PhotosUploader;
import com.swipedelete.zero.ui.screens.dashboard.DashboardViewModel;
import com.swipedelete.zero.ui.screens.dashboard.DashboardViewModel_HiltModules;
import com.swipedelete.zero.ui.screens.dashboard.DashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.swipedelete.zero.ui.screens.dashboard.DashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.swipedelete.zero.ui.screens.dual.DualCardViewModel;
import com.swipedelete.zero.ui.screens.dual.DualCardViewModel_HiltModules;
import com.swipedelete.zero.ui.screens.dual.DualCardViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.swipedelete.zero.ui.screens.dual.DualCardViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.swipedelete.zero.ui.screens.settings.SettingsViewModel;
import com.swipedelete.zero.ui.screens.settings.SettingsViewModel_HiltModules;
import com.swipedelete.zero.ui.screens.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.swipedelete.zero.ui.screens.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.swipedelete.zero.ui.screens.staging.StagingViewModel;
import com.swipedelete.zero.ui.screens.staging.StagingViewModel_HiltModules;
import com.swipedelete.zero.ui.screens.staging.StagingViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.swipedelete.zero.ui.screens.staging.StagingViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel;
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel_HiltModules;
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

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
public final class DaggerSwipeDeleteApplication_HiltComponents_SingletonC {
  private DaggerSwipeDeleteApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public SwipeDeleteApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements SwipeDeleteApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements SwipeDeleteApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements SwipeDeleteApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements SwipeDeleteApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements SwipeDeleteApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements SwipeDeleteApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements SwipeDeleteApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public SwipeDeleteApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends SwipeDeleteApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends SwipeDeleteApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends SwipeDeleteApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends SwipeDeleteApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(ImmutableMap.<String, Boolean>of(DashboardViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DashboardViewModel_HiltModules.KeyModule.provide(), DualCardViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, DualCardViewModel_HiltModules.KeyModule.provide(), SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide(), StagingViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, StagingViewModel_HiltModules.KeyModule.provide(), SwipeEngineViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SwipeEngineViewModel_HiltModules.KeyModule.provide()));
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends SwipeDeleteApplication_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<DashboardViewModel> dashboardViewModelProvider;

    private Provider<DualCardViewModel> dualCardViewModelProvider;

    private Provider<SettingsViewModel> settingsViewModelProvider;

    private Provider<StagingViewModel> stagingViewModelProvider;

    private Provider<SwipeEngineViewModel> swipeEngineViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.dashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.dualCardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.stagingViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.swipeEngineViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(ImmutableMap.<String, javax.inject.Provider<ViewModel>>of(DashboardViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) dashboardViewModelProvider), DualCardViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) dualCardViewModelProvider), SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) settingsViewModelProvider), StagingViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) stagingViewModelProvider), SwipeEngineViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) swipeEngineViewModelProvider)));
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return ImmutableMap.<Class<?>, Object>of();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.swipedelete.zero.ui.screens.dashboard.DashboardViewModel 
          return (T) new DashboardViewModel(singletonCImpl.deckRepositoryProvider.get(), singletonCImpl.stagingRepositoryProvider.get());

          case 1: // com.swipedelete.zero.ui.screens.dual.DualCardViewModel 
          return (T) new DualCardViewModel(singletonCImpl.deckRepositoryProvider.get(), singletonCImpl.stagingRepositoryProvider.get(), singletonCImpl.backupRepositoryProvider.get(), viewModelCImpl.savedStateHandle);

          case 2: // com.swipedelete.zero.ui.screens.settings.SettingsViewModel 
          return (T) new SettingsViewModel(singletonCImpl.exclusionRepositoryProvider.get(), singletonCImpl.driveCloudBackupProvider.get(), singletonCImpl.backupRepositoryProvider.get());

          case 3: // com.swipedelete.zero.ui.screens.staging.StagingViewModel 
          return (T) new StagingViewModel(singletonCImpl.stagingRepositoryProvider.get(), singletonCImpl.purgeEngineProvider.get(), singletonCImpl.statsStoreProvider.get());

          case 4: // com.swipedelete.zero.ui.screens.swipe.SwipeEngineViewModel 
          return (T) new SwipeEngineViewModel(singletonCImpl.deckRepositoryProvider.get(), singletonCImpl.stagingRepositoryProvider.get(), singletonCImpl.exclusionRepositoryProvider.get(), singletonCImpl.backupRepositoryProvider.get(), singletonCImpl.googlePhotosArchiveProvider.get(), singletonCImpl.videoMetadataExtractorProvider.get(), singletonCImpl.mediaAnalysisDao(), singletonCImpl.mediaPreloaderProvider.get(), viewModelCImpl.savedStateHandle);

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends SwipeDeleteApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends SwipeDeleteApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends SwipeDeleteApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<MediaStoreRepository> mediaStoreRepositoryProvider;

    private Provider<AppDatabase> provideDatabaseProvider;

    private Provider<VideoMetadataExtractor> videoMetadataExtractorProvider;

    private Provider<MediaAnalysisWorker_AssistedFactory> mediaAnalysisWorker_AssistedFactoryProvider;

    private Provider<PhotosUploader> photosUploaderProvider;

    private Provider<PhotosUploadWorker_AssistedFactory> photosUploadWorker_AssistedFactoryProvider;

    private Provider<AnalysisScheduler> analysisSchedulerProvider;

    private Provider<DeckBuilder> deckBuilderProvider;

    private Provider<DeckRepository> deckRepositoryProvider;

    private Provider<StagingRepository> stagingRepositoryProvider;

    private Provider<BackupRepository> backupRepositoryProvider;

    private Provider<ExclusionRepository> exclusionRepositoryProvider;

    private Provider<DriveCloudBackup> driveCloudBackupProvider;

    private Provider<SafStorageBridge> safStorageBridgeProvider;

    private Provider<StoragePermissionManager> storagePermissionManagerProvider;

    private Provider<PurgeEngine> purgeEngineProvider;

    private Provider<StatsStore> statsStoreProvider;

    private Provider<GooglePhotosArchive> googlePhotosArchiveProvider;

    private Provider<MediaPreloader> mediaPreloaderProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    private MediaAnalysisDao mediaAnalysisDao() {
      return DatabaseModule_ProvideMediaAnalysisDaoFactory.provideMediaAnalysisDao(provideDatabaseProvider.get());
    }

    private CloudUploadDao cloudUploadDao() {
      return DatabaseModule_ProvideCloudUploadDaoFactory.provideCloudUploadDao(provideDatabaseProvider.get());
    }

    private BackedUpFileDao backedUpFileDao() {
      return DatabaseModule_ProvideBackedUpFileDaoFactory.provideBackedUpFileDao(provideDatabaseProvider.get());
    }

    private StagedFileDao stagedFileDao() {
      return DatabaseModule_ProvideStagedFileDaoFactory.provideStagedFileDao(provideDatabaseProvider.get());
    }

    private Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return ImmutableMap.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>of("com.swipedelete.zero.domain.scanner.MediaAnalysisWorker", ((Provider) mediaAnalysisWorker_AssistedFactoryProvider), "com.swipedelete.zero.photos.PhotosUploadWorker", ((Provider) photosUploadWorker_AssistedFactoryProvider));
    }

    private HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    private ExclusionDao exclusionDao() {
      return DatabaseModule_ProvideExclusionDaoFactory.provideExclusionDao(provideDatabaseProvider.get());
    }

    private DeckSessionDao deckSessionDao() {
      return DatabaseModule_ProvideDeckSessionDaoFactory.provideDeckSessionDao(provideDatabaseProvider.get());
    }

    private KeptFileDao keptFileDao() {
      return DatabaseModule_ProvideKeptFileDaoFactory.provideKeptFileDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.mediaStoreRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<MediaStoreRepository>(singletonCImpl, 1));
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 2));
      this.videoMetadataExtractorProvider = DoubleCheck.provider(new SwitchingProvider<VideoMetadataExtractor>(singletonCImpl, 3));
      this.mediaAnalysisWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<MediaAnalysisWorker_AssistedFactory>(singletonCImpl, 0));
      this.photosUploaderProvider = DoubleCheck.provider(new SwitchingProvider<PhotosUploader>(singletonCImpl, 5));
      this.photosUploadWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<PhotosUploadWorker_AssistedFactory>(singletonCImpl, 4));
      this.analysisSchedulerProvider = DoubleCheck.provider(new SwitchingProvider<AnalysisScheduler>(singletonCImpl, 6));
      this.deckBuilderProvider = DoubleCheck.provider(new SwitchingProvider<DeckBuilder>(singletonCImpl, 8));
      this.deckRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<DeckRepository>(singletonCImpl, 7));
      this.stagingRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<StagingRepository>(singletonCImpl, 9));
      this.backupRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<BackupRepository>(singletonCImpl, 10));
      this.exclusionRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<ExclusionRepository>(singletonCImpl, 11));
      this.driveCloudBackupProvider = DoubleCheck.provider(new SwitchingProvider<DriveCloudBackup>(singletonCImpl, 12));
      this.safStorageBridgeProvider = DoubleCheck.provider(new SwitchingProvider<SafStorageBridge>(singletonCImpl, 14));
      this.storagePermissionManagerProvider = DoubleCheck.provider(new SwitchingProvider<StoragePermissionManager>(singletonCImpl, 15));
      this.purgeEngineProvider = DoubleCheck.provider(new SwitchingProvider<PurgeEngine>(singletonCImpl, 13));
      this.statsStoreProvider = DoubleCheck.provider(new SwitchingProvider<StatsStore>(singletonCImpl, 16));
      this.googlePhotosArchiveProvider = DoubleCheck.provider(new SwitchingProvider<GooglePhotosArchive>(singletonCImpl, 17));
      this.mediaPreloaderProvider = DoubleCheck.provider(new SwitchingProvider<MediaPreloader>(singletonCImpl, 18));
    }

    @Override
    public void injectSwipeDeleteApplication(SwipeDeleteApplication swipeDeleteApplication) {
      injectSwipeDeleteApplication2(swipeDeleteApplication);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return ImmutableSet.<Boolean>of();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private SwipeDeleteApplication injectSwipeDeleteApplication2(SwipeDeleteApplication instance) {
      SwipeDeleteApplication_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      SwipeDeleteApplication_MembersInjector.injectAnalysisScheduler(instance, analysisSchedulerProvider.get());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.swipedelete.zero.domain.scanner.MediaAnalysisWorker_AssistedFactory 
          return (T) new MediaAnalysisWorker_AssistedFactory() {
            @Override
            public MediaAnalysisWorker create(Context appContext, WorkerParameters params) {
              return new MediaAnalysisWorker(appContext, params, singletonCImpl.mediaStoreRepositoryProvider.get(), singletonCImpl.mediaAnalysisDao(), singletonCImpl.videoMetadataExtractorProvider.get());
            }
          };

          case 1: // com.swipedelete.zero.data.repository.MediaStoreRepository 
          return (T) new MediaStoreRepository(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.swipedelete.zero.data.local.AppDatabase 
          return (T) DatabaseModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // com.swipedelete.zero.domain.scanner.VideoMetadataExtractor 
          return (T) new VideoMetadataExtractor(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 4: // com.swipedelete.zero.photos.PhotosUploadWorker_AssistedFactory 
          return (T) new PhotosUploadWorker_AssistedFactory() {
            @Override
            public PhotosUploadWorker create(Context appContext2, WorkerParameters params2) {
              return new PhotosUploadWorker(appContext2, params2, singletonCImpl.cloudUploadDao(), singletonCImpl.backedUpFileDao(), singletonCImpl.stagedFileDao(), singletonCImpl.photosUploaderProvider.get());
            }
          };

          case 5: // com.swipedelete.zero.photos.PhotosUploader 
          return (T) new PhotosUploader();

          case 6: // com.swipedelete.zero.domain.scanner.AnalysisScheduler 
          return (T) new AnalysisScheduler(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 7: // com.swipedelete.zero.data.repository.DeckRepository 
          return (T) new DeckRepository(singletonCImpl.deckBuilderProvider.get(), singletonCImpl.deckSessionDao());

          case 8: // com.swipedelete.zero.domain.scanner.DeckBuilder 
          return (T) new DeckBuilder(singletonCImpl.mediaStoreRepositoryProvider.get(), singletonCImpl.mediaAnalysisDao(), singletonCImpl.exclusionDao());

          case 9: // com.swipedelete.zero.data.repository.StagingRepository 
          return (T) new StagingRepository(singletonCImpl.stagedFileDao());

          case 10: // com.swipedelete.zero.data.repository.BackupRepository 
          return (T) new BackupRepository(singletonCImpl.keptFileDao(), singletonCImpl.backedUpFileDao());

          case 11: // com.swipedelete.zero.data.repository.ExclusionRepository 
          return (T) new ExclusionRepository(singletonCImpl.exclusionDao());

          case 12: // com.swipedelete.zero.backup.DriveCloudBackup 
          return (T) new DriveCloudBackup(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.backupRepositoryProvider.get());

          case 13: // com.swipedelete.zero.data.repository.PurgeEngine 
          return (T) new PurgeEngine(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.mediaStoreRepositoryProvider.get(), singletonCImpl.safStorageBridgeProvider.get(), singletonCImpl.storagePermissionManagerProvider.get());

          case 14: // com.swipedelete.zero.data.repository.SafStorageBridge 
          return (T) new SafStorageBridge(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 15: // com.swipedelete.zero.data.repository.StoragePermissionManager 
          return (T) new StoragePermissionManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 16: // com.swipedelete.zero.data.repository.StatsStore 
          return (T) new StatsStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 17: // com.swipedelete.zero.photos.GooglePhotosArchive 
          return (T) new GooglePhotosArchive(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.cloudUploadDao());

          case 18: // com.swipedelete.zero.data.repository.MediaPreloader 
          return (T) new MediaPreloader(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
