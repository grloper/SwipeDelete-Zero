package com.swipedelete.zero.di;

import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.BackedUpFileDao;
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
public final class DatabaseModule_ProvideBackedUpFileDaoFactory implements Factory<BackedUpFileDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideBackedUpFileDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public BackedUpFileDao get() {
    return provideBackedUpFileDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideBackedUpFileDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideBackedUpFileDaoFactory(dbProvider);
  }

  public static BackedUpFileDao provideBackedUpFileDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideBackedUpFileDao(db));
  }
}
