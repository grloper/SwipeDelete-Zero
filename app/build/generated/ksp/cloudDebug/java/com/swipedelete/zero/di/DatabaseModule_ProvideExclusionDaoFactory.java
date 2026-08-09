package com.swipedelete.zero.di;

import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.ExclusionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class DatabaseModule_ProvideExclusionDaoFactory implements Factory<ExclusionDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideExclusionDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ExclusionDao get() {
    return provideExclusionDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideExclusionDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideExclusionDaoFactory(dbProvider);
  }

  public static ExclusionDao provideExclusionDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideExclusionDao(db));
  }
}
