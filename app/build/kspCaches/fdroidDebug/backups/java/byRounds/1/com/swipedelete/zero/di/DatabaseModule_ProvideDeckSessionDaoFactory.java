package com.swipedelete.zero.di;

import com.swipedelete.zero.data.local.AppDatabase;
import com.swipedelete.zero.data.local.DeckSessionDao;
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
public final class DatabaseModule_ProvideDeckSessionDaoFactory implements Factory<DeckSessionDao> {
  private final Provider<AppDatabase> dbProvider;

  public DatabaseModule_ProvideDeckSessionDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DeckSessionDao get() {
    return provideDeckSessionDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideDeckSessionDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideDeckSessionDaoFactory(dbProvider);
  }

  public static DeckSessionDao provideDeckSessionDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDeckSessionDao(db));
  }
}
