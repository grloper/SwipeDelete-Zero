package com.swipedelete.zero.data.repository;

import com.swipedelete.zero.data.local.StagedFileDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class StagingRepository_Factory implements Factory<StagingRepository> {
  private final Provider<StagedFileDao> daoProvider;

  public StagingRepository_Factory(Provider<StagedFileDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public StagingRepository get() {
    return newInstance(daoProvider.get());
  }

  public static StagingRepository_Factory create(Provider<StagedFileDao> daoProvider) {
    return new StagingRepository_Factory(daoProvider);
  }

  public static StagingRepository newInstance(StagedFileDao dao) {
    return new StagingRepository(dao);
  }
}
