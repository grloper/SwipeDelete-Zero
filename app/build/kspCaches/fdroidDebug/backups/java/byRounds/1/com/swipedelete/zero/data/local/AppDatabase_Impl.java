package com.swipedelete.zero.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile StagedFileDao _stagedFileDao;

  private volatile DeckSessionDao _deckSessionDao;

  private volatile ExclusionDao _exclusionDao;

  private volatile MediaAnalysisDao _mediaAnalysisDao;

  private volatile KeptFileDao _keptFileDao;

  private volatile BackedUpFileDao _backedUpFileDao;

  private volatile CloudUploadDao _cloudUploadDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `staged_files` (`contentUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `relativePath` TEXT, `stagedAtMillis` INTEGER NOT NULL, `sourceDeckId` TEXT, PRIMARY KEY(`contentUri`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `deck_sessions` (`deckId` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `cursor` INTEGER NOT NULL, `totalCount` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`deckId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `exclusions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `uri` TEXT, `perceptualHash` INTEGER, `folderPath` TEXT, `label` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exclusions_perceptualHash` ON `exclusions` (`perceptualHash`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exclusions_folderPath` ON `exclusions` (`folderPath`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `media_analysis` (`mediaId` INTEGER NOT NULL, `contentUri` TEXT NOT NULL, `dHash` INTEGER, `pHash` INTEGER, `sharpnessVariance` REAL, `meanLuma` REAL, `isBlurry` INTEGER, `sizeBytes` INTEGER NOT NULL, `analyzedAtMillis` INTEGER NOT NULL, `videoCodec` TEXT, `frameRate` REAL, `bitrateBps` INTEGER, `bimodality` REAL, PRIMARY KEY(`mediaId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `kept_files` (`contentUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `keptAtMillis` INTEGER NOT NULL, `starred` INTEGER NOT NULL, PRIMARY KEY(`contentUri`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `backed_up_files` (`contentUri` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `uploadedAtMillis` INTEGER NOT NULL, `destination` TEXT NOT NULL, `displayName` TEXT NOT NULL, `remoteState` TEXT NOT NULL, `verifiedAtMillis` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`contentUri`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cloud_uploads` (`contentUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `state` TEXT NOT NULL, `uploadUrl` TEXT, `bytesUploaded` INTEGER NOT NULL, `uploadToken` TEXT, `mediaItemId` TEXT, `attempts` INTEGER NOT NULL, `lastError` TEXT, `enqueuedAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`contentUri`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '926545ba7d0855957eae68b5376632fc')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `staged_files`");
        db.execSQL("DROP TABLE IF EXISTS `deck_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `exclusions`");
        db.execSQL("DROP TABLE IF EXISTS `media_analysis`");
        db.execSQL("DROP TABLE IF EXISTS `kept_files`");
        db.execSQL("DROP TABLE IF EXISTS `backed_up_files`");
        db.execSQL("DROP TABLE IF EXISTS `cloud_uploads`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsStagedFiles = new HashMap<String, TableInfo.Column>(8);
        _columnsStagedFiles.put("contentUri", new TableInfo.Column("contentUri", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("mediaType", new TableInfo.Column("mediaType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("relativePath", new TableInfo.Column("relativePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("stagedAtMillis", new TableInfo.Column("stagedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStagedFiles.put("sourceDeckId", new TableInfo.Column("sourceDeckId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStagedFiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStagedFiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStagedFiles = new TableInfo("staged_files", _columnsStagedFiles, _foreignKeysStagedFiles, _indicesStagedFiles);
        final TableInfo _existingStagedFiles = TableInfo.read(db, "staged_files");
        if (!_infoStagedFiles.equals(_existingStagedFiles)) {
          return new RoomOpenHelper.ValidationResult(false, "staged_files(com.swipedelete.zero.data.local.StagedFileEntity).\n"
                  + " Expected:\n" + _infoStagedFiles + "\n"
                  + " Found:\n" + _existingStagedFiles);
        }
        final HashMap<String, TableInfo.Column> _columnsDeckSessions = new HashMap<String, TableInfo.Column>(6);
        _columnsDeckSessions.put("deckId", new TableInfo.Column("deckId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeckSessions.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeckSessions.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeckSessions.put("cursor", new TableInfo.Column("cursor", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeckSessions.put("totalCount", new TableInfo.Column("totalCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDeckSessions.put("updatedAtMillis", new TableInfo.Column("updatedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDeckSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDeckSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDeckSessions = new TableInfo("deck_sessions", _columnsDeckSessions, _foreignKeysDeckSessions, _indicesDeckSessions);
        final TableInfo _existingDeckSessions = TableInfo.read(db, "deck_sessions");
        if (!_infoDeckSessions.equals(_existingDeckSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "deck_sessions(com.swipedelete.zero.data.local.DeckSessionEntity).\n"
                  + " Expected:\n" + _infoDeckSessions + "\n"
                  + " Found:\n" + _existingDeckSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsExclusions = new HashMap<String, TableInfo.Column>(7);
        _columnsExclusions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExclusions.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExclusions.put("uri", new TableInfo.Column("uri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExclusions.put("perceptualHash", new TableInfo.Column("perceptualHash", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExclusions.put("folderPath", new TableInfo.Column("folderPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExclusions.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExclusions.put("createdAtMillis", new TableInfo.Column("createdAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysExclusions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesExclusions = new HashSet<TableInfo.Index>(2);
        _indicesExclusions.add(new TableInfo.Index("index_exclusions_perceptualHash", false, Arrays.asList("perceptualHash"), Arrays.asList("ASC")));
        _indicesExclusions.add(new TableInfo.Index("index_exclusions_folderPath", false, Arrays.asList("folderPath"), Arrays.asList("ASC")));
        final TableInfo _infoExclusions = new TableInfo("exclusions", _columnsExclusions, _foreignKeysExclusions, _indicesExclusions);
        final TableInfo _existingExclusions = TableInfo.read(db, "exclusions");
        if (!_infoExclusions.equals(_existingExclusions)) {
          return new RoomOpenHelper.ValidationResult(false, "exclusions(com.swipedelete.zero.data.local.ExclusionEntity).\n"
                  + " Expected:\n" + _infoExclusions + "\n"
                  + " Found:\n" + _existingExclusions);
        }
        final HashMap<String, TableInfo.Column> _columnsMediaAnalysis = new HashMap<String, TableInfo.Column>(13);
        _columnsMediaAnalysis.put("mediaId", new TableInfo.Column("mediaId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("contentUri", new TableInfo.Column("contentUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("dHash", new TableInfo.Column("dHash", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("pHash", new TableInfo.Column("pHash", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("sharpnessVariance", new TableInfo.Column("sharpnessVariance", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("meanLuma", new TableInfo.Column("meanLuma", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("isBlurry", new TableInfo.Column("isBlurry", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("analyzedAtMillis", new TableInfo.Column("analyzedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("videoCodec", new TableInfo.Column("videoCodec", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("frameRate", new TableInfo.Column("frameRate", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("bitrateBps", new TableInfo.Column("bitrateBps", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMediaAnalysis.put("bimodality", new TableInfo.Column("bimodality", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMediaAnalysis = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMediaAnalysis = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMediaAnalysis = new TableInfo("media_analysis", _columnsMediaAnalysis, _foreignKeysMediaAnalysis, _indicesMediaAnalysis);
        final TableInfo _existingMediaAnalysis = TableInfo.read(db, "media_analysis");
        if (!_infoMediaAnalysis.equals(_existingMediaAnalysis)) {
          return new RoomOpenHelper.ValidationResult(false, "media_analysis(com.swipedelete.zero.data.local.MediaAnalysisEntity).\n"
                  + " Expected:\n" + _infoMediaAnalysis + "\n"
                  + " Found:\n" + _existingMediaAnalysis);
        }
        final HashMap<String, TableInfo.Column> _columnsKeptFiles = new HashMap<String, TableInfo.Column>(6);
        _columnsKeptFiles.put("contentUri", new TableInfo.Column("contentUri", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeptFiles.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeptFiles.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeptFiles.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeptFiles.put("keptAtMillis", new TableInfo.Column("keptAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeptFiles.put("starred", new TableInfo.Column("starred", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKeptFiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesKeptFiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoKeptFiles = new TableInfo("kept_files", _columnsKeptFiles, _foreignKeysKeptFiles, _indicesKeptFiles);
        final TableInfo _existingKeptFiles = TableInfo.read(db, "kept_files");
        if (!_infoKeptFiles.equals(_existingKeptFiles)) {
          return new RoomOpenHelper.ValidationResult(false, "kept_files(com.swipedelete.zero.data.local.KeptFileEntity).\n"
                  + " Expected:\n" + _infoKeptFiles + "\n"
                  + " Found:\n" + _existingKeptFiles);
        }
        final HashMap<String, TableInfo.Column> _columnsBackedUpFiles = new HashMap<String, TableInfo.Column>(9);
        _columnsBackedUpFiles.put("contentUri", new TableInfo.Column("contentUri", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("remoteId", new TableInfo.Column("remoteId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("uploadedAtMillis", new TableInfo.Column("uploadedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("destination", new TableInfo.Column("destination", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("remoteState", new TableInfo.Column("remoteState", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("verifiedAtMillis", new TableInfo.Column("verifiedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBackedUpFiles.put("lastError", new TableInfo.Column("lastError", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBackedUpFiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBackedUpFiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBackedUpFiles = new TableInfo("backed_up_files", _columnsBackedUpFiles, _foreignKeysBackedUpFiles, _indicesBackedUpFiles);
        final TableInfo _existingBackedUpFiles = TableInfo.read(db, "backed_up_files");
        if (!_infoBackedUpFiles.equals(_existingBackedUpFiles)) {
          return new RoomOpenHelper.ValidationResult(false, "backed_up_files(com.swipedelete.zero.data.local.BackedUpFileEntity).\n"
                  + " Expected:\n" + _infoBackedUpFiles + "\n"
                  + " Found:\n" + _existingBackedUpFiles);
        }
        final HashMap<String, TableInfo.Column> _columnsCloudUploads = new HashMap<String, TableInfo.Column>(13);
        _columnsCloudUploads.put("contentUri", new TableInfo.Column("contentUri", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("state", new TableInfo.Column("state", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("uploadUrl", new TableInfo.Column("uploadUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("bytesUploaded", new TableInfo.Column("bytesUploaded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("uploadToken", new TableInfo.Column("uploadToken", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("mediaItemId", new TableInfo.Column("mediaItemId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("attempts", new TableInfo.Column("attempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("lastError", new TableInfo.Column("lastError", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("enqueuedAtMillis", new TableInfo.Column("enqueuedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCloudUploads.put("updatedAtMillis", new TableInfo.Column("updatedAtMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCloudUploads = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCloudUploads = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCloudUploads = new TableInfo("cloud_uploads", _columnsCloudUploads, _foreignKeysCloudUploads, _indicesCloudUploads);
        final TableInfo _existingCloudUploads = TableInfo.read(db, "cloud_uploads");
        if (!_infoCloudUploads.equals(_existingCloudUploads)) {
          return new RoomOpenHelper.ValidationResult(false, "cloud_uploads(com.swipedelete.zero.data.local.CloudUploadEntity).\n"
                  + " Expected:\n" + _infoCloudUploads + "\n"
                  + " Found:\n" + _existingCloudUploads);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "926545ba7d0855957eae68b5376632fc", "919fff846f6e1024f1bf5e2d0a856698");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "staged_files","deck_sessions","exclusions","media_analysis","kept_files","backed_up_files","cloud_uploads");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `staged_files`");
      _db.execSQL("DELETE FROM `deck_sessions`");
      _db.execSQL("DELETE FROM `exclusions`");
      _db.execSQL("DELETE FROM `media_analysis`");
      _db.execSQL("DELETE FROM `kept_files`");
      _db.execSQL("DELETE FROM `backed_up_files`");
      _db.execSQL("DELETE FROM `cloud_uploads`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(StagedFileDao.class, StagedFileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DeckSessionDao.class, DeckSessionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ExclusionDao.class, ExclusionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MediaAnalysisDao.class, MediaAnalysisDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(KeptFileDao.class, KeptFileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BackedUpFileDao.class, BackedUpFileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CloudUploadDao.class, CloudUploadDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public StagedFileDao stagedFileDao() {
    if (_stagedFileDao != null) {
      return _stagedFileDao;
    } else {
      synchronized(this) {
        if(_stagedFileDao == null) {
          _stagedFileDao = new StagedFileDao_Impl(this);
        }
        return _stagedFileDao;
      }
    }
  }

  @Override
  public DeckSessionDao deckSessionDao() {
    if (_deckSessionDao != null) {
      return _deckSessionDao;
    } else {
      synchronized(this) {
        if(_deckSessionDao == null) {
          _deckSessionDao = new DeckSessionDao_Impl(this);
        }
        return _deckSessionDao;
      }
    }
  }

  @Override
  public ExclusionDao exclusionDao() {
    if (_exclusionDao != null) {
      return _exclusionDao;
    } else {
      synchronized(this) {
        if(_exclusionDao == null) {
          _exclusionDao = new ExclusionDao_Impl(this);
        }
        return _exclusionDao;
      }
    }
  }

  @Override
  public MediaAnalysisDao mediaAnalysisDao() {
    if (_mediaAnalysisDao != null) {
      return _mediaAnalysisDao;
    } else {
      synchronized(this) {
        if(_mediaAnalysisDao == null) {
          _mediaAnalysisDao = new MediaAnalysisDao_Impl(this);
        }
        return _mediaAnalysisDao;
      }
    }
  }

  @Override
  public KeptFileDao keptFileDao() {
    if (_keptFileDao != null) {
      return _keptFileDao;
    } else {
      synchronized(this) {
        if(_keptFileDao == null) {
          _keptFileDao = new KeptFileDao_Impl(this);
        }
        return _keptFileDao;
      }
    }
  }

  @Override
  public BackedUpFileDao backedUpFileDao() {
    if (_backedUpFileDao != null) {
      return _backedUpFileDao;
    } else {
      synchronized(this) {
        if(_backedUpFileDao == null) {
          _backedUpFileDao = new BackedUpFileDao_Impl(this);
        }
        return _backedUpFileDao;
      }
    }
  }

  @Override
  public CloudUploadDao cloudUploadDao() {
    if (_cloudUploadDao != null) {
      return _cloudUploadDao;
    } else {
      synchronized(this) {
        if(_cloudUploadDao == null) {
          _cloudUploadDao = new CloudUploadDao_Impl(this);
        }
        return _cloudUploadDao;
      }
    }
  }
}
