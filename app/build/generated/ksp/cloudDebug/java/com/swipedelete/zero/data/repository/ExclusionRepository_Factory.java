package com.swipedelete.zero.data.repository;

import com.swipedelete.zero.data.local.ExclusionDao;
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
public final class ExclusionRepository_Factory implements Factory<ExclusionRepository> {
  private final Provider<ExclusionDao> daoProvider;

  public ExclusionRepository_Factory(Provider<ExclusionDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public ExclusionRepository get() {
    return newInstance(daoProvider.get());
  }

  public static ExclusionRepository_Factory create(Provider<ExclusionDao> daoProvider) {
    return new ExclusionRepository_Factory(daoProvider);
  }

  public static ExclusionRepository newInstance(ExclusionDao dao) {
    return new ExclusionRepository(dao);
  }
}
