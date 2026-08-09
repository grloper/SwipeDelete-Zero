package com.swipedelete.zero.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MediaAnalysisDao_Impl implements MediaAnalysisDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<MediaAnalysisEntity> __upsertionAdapterOfMediaAnalysisEntity;

  public MediaAnalysisDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfMediaAnalysisEntity = new EntityUpsertionAdapter<MediaAnalysisEntity>(new EntityInsertionAdapter<MediaAnalysisEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `media_analysis` (`mediaId`,`contentUri`,`dHash`,`pHash`,`sharpnessVariance`,`meanLuma`,`isBlurry`,`sizeBytes`,`analyzedAtMillis`,`videoCodec`,`frameRate`,`bitrateBps`,`bimodality`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MediaAnalysisEntity entity) {
        statement.bindLong(1, entity.getMediaId());
        statement.bindString(2, entity.getContentUri());
        if (entity.getDHash() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getDHash());
        }
        if (entity.getPHash() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPHash());
        }
        if (entity.getSharpnessVariance() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getSharpnessVariance());
        }
        if (entity.getMeanLuma() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getMeanLuma());
        }
        final Integer _tmp = entity.isBlurry() == null ? null : (entity.isBlurry() ? 1 : 0);
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, _tmp);
        }
        statement.bindLong(8, entity.getSizeBytes());
        statement.bindLong(9, entity.getAnalyzedAtMillis());
        if (entity.getVideoCodec() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getVideoCodec());
        }
        if (entity.getFrameRate() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getFrameRate());
        }
        if (entity.getBitrateBps() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getBitrateBps());
        }
        if (entity.getBimodality() == null) {
          statement.bindNull(13);
        } else {
          statement.bindDouble(13, entity.getBimodality());
        }
      }
    }, new EntityDeletionOrUpdateAdapter<MediaAnalysisEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `media_analysis` SET `mediaId` = ?,`contentUri` = ?,`dHash` = ?,`pHash` = ?,`sharpnessVariance` = ?,`meanLuma` = ?,`isBlurry` = ?,`sizeBytes` = ?,`analyzedAtMillis` = ?,`videoCodec` = ?,`frameRate` = ?,`bitrateBps` = ?,`bimodality` = ? WHERE `mediaId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MediaAnalysisEntity entity) {
        statement.bindLong(1, entity.getMediaId());
        statement.bindString(2, entity.getContentUri());
        if (entity.getDHash() == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, entity.getDHash());
        }
        if (entity.getPHash() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPHash());
        }
        if (entity.getSharpnessVariance() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getSharpnessVariance());
        }
        if (entity.getMeanLuma() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getMeanLuma());
        }
        final Integer _tmp = entity.isBlurry() == null ? null : (entity.isBlurry() ? 1 : 0);
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, _tmp);
        }
        statement.bindLong(8, entity.getSizeBytes());
        statement.bindLong(9, entity.getAnalyzedAtMillis());
        if (entity.getVideoCodec() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getVideoCodec());
        }
        if (entity.getFrameRate() == null) {
          statement.bindNull(11);
        } else {
          statement.bindDouble(11, entity.getFrameRate());
        }
        if (entity.getBitrateBps() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getBitrateBps());
        }
        if (entity.getBimodality() == null) {
          statement.bindNull(13);
        } else {
          statement.bindDouble(13, entity.getBimodality());
        }
        statement.bindLong(14, entity.getMediaId());
      }
    });
  }

  @Override
  public Object upsert(final MediaAnalysisEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfMediaAnalysisEntity.upsert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<MediaAnalysisEntity> entities,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfMediaAnalysisEntity.upsert(entities);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final long id, final Continuation<? super MediaAnalysisEntity> $completion) {
    final String _sql = "SELECT * FROM media_analysis WHERE mediaId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<MediaAnalysisEntity>() {
      @Override
      @Nullable
      public MediaAnalysisEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaId");
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDHash = CursorUtil.getColumnIndexOrThrow(_cursor, "dHash");
          final int _cursorIndexOfPHash = CursorUtil.getColumnIndexOrThrow(_cursor, "pHash");
          final int _cursorIndexOfSharpnessVariance = CursorUtil.getColumnIndexOrThrow(_cursor, "sharpnessVariance");
          final int _cursorIndexOfMeanLuma = CursorUtil.getColumnIndexOrThrow(_cursor, "meanLuma");
          final int _cursorIndexOfIsBlurry = CursorUtil.getColumnIndexOrThrow(_cursor, "isBlurry");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfAnalyzedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "analyzedAtMillis");
          final int _cursorIndexOfVideoCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "videoCodec");
          final int _cursorIndexOfFrameRate = CursorUtil.getColumnIndexOrThrow(_cursor, "frameRate");
          final int _cursorIndexOfBitrateBps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateBps");
          final int _cursorIndexOfBimodality = CursorUtil.getColumnIndexOrThrow(_cursor, "bimodality");
          final MediaAnalysisEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpMediaId;
            _tmpMediaId = _cursor.getLong(_cursorIndexOfMediaId);
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final Long _tmpDHash;
            if (_cursor.isNull(_cursorIndexOfDHash)) {
              _tmpDHash = null;
            } else {
              _tmpDHash = _cursor.getLong(_cursorIndexOfDHash);
            }
            final Long _tmpPHash;
            if (_cursor.isNull(_cursorIndexOfPHash)) {
              _tmpPHash = null;
            } else {
              _tmpPHash = _cursor.getLong(_cursorIndexOfPHash);
            }
            final Double _tmpSharpnessVariance;
            if (_cursor.isNull(_cursorIndexOfSharpnessVariance)) {
              _tmpSharpnessVariance = null;
            } else {
              _tmpSharpnessVariance = _cursor.getDouble(_cursorIndexOfSharpnessVariance);
            }
            final Double _tmpMeanLuma;
            if (_cursor.isNull(_cursorIndexOfMeanLuma)) {
              _tmpMeanLuma = null;
            } else {
              _tmpMeanLuma = _cursor.getDouble(_cursorIndexOfMeanLuma);
            }
            final Boolean _tmpIsBlurry;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfIsBlurry)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfIsBlurry);
            }
            _tmpIsBlurry = _tmp == null ? null : _tmp != 0;
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpAnalyzedAtMillis;
            _tmpAnalyzedAtMillis = _cursor.getLong(_cursorIndexOfAnalyzedAtMillis);
            final String _tmpVideoCodec;
            if (_cursor.isNull(_cursorIndexOfVideoCodec)) {
              _tmpVideoCodec = null;
            } else {
              _tmpVideoCodec = _cursor.getString(_cursorIndexOfVideoCodec);
            }
            final Float _tmpFrameRate;
            if (_cursor.isNull(_cursorIndexOfFrameRate)) {
              _tmpFrameRate = null;
            } else {
              _tmpFrameRate = _cursor.getFloat(_cursorIndexOfFrameRate);
            }
            final Long _tmpBitrateBps;
            if (_cursor.isNull(_cursorIndexOfBitrateBps)) {
              _tmpBitrateBps = null;
            } else {
              _tmpBitrateBps = _cursor.getLong(_cursorIndexOfBitrateBps);
            }
            final Double _tmpBimodality;
            if (_cursor.isNull(_cursorIndexOfBimodality)) {
              _tmpBimodality = null;
            } else {
              _tmpBimodality = _cursor.getDouble(_cursorIndexOfBimodality);
            }
            _result = new MediaAnalysisEntity(_tmpMediaId,_tmpContentUri,_tmpDHash,_tmpPHash,_tmpSharpnessVariance,_tmpMeanLuma,_tmpIsBlurry,_tmpSizeBytes,_tmpAnalyzedAtMillis,_tmpVideoCodec,_tmpFrameRate,_tmpBitrateBps,_tmpBimodality);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object analyzedIds(final Continuation<? super List<Long>> $completion) {
    final String _sql = "SELECT mediaId FROM media_analysis";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<Long> _result = new ArrayList<Long>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Long _item;
            _item = _cursor.getLong(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object blurryItems(final Continuation<? super List<MediaAnalysisEntity>> $completion) {
    final String _sql = "SELECT * FROM media_analysis WHERE isBlurry = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MediaAnalysisEntity>>() {
      @Override
      @NonNull
      public List<MediaAnalysisEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaId");
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDHash = CursorUtil.getColumnIndexOrThrow(_cursor, "dHash");
          final int _cursorIndexOfPHash = CursorUtil.getColumnIndexOrThrow(_cursor, "pHash");
          final int _cursorIndexOfSharpnessVariance = CursorUtil.getColumnIndexOrThrow(_cursor, "sharpnessVariance");
          final int _cursorIndexOfMeanLuma = CursorUtil.getColumnIndexOrThrow(_cursor, "meanLuma");
          final int _cursorIndexOfIsBlurry = CursorUtil.getColumnIndexOrThrow(_cursor, "isBlurry");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfAnalyzedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "analyzedAtMillis");
          final int _cursorIndexOfVideoCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "videoCodec");
          final int _cursorIndexOfFrameRate = CursorUtil.getColumnIndexOrThrow(_cursor, "frameRate");
          final int _cursorIndexOfBitrateBps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateBps");
          final int _cursorIndexOfBimodality = CursorUtil.getColumnIndexOrThrow(_cursor, "bimodality");
          final List<MediaAnalysisEntity> _result = new ArrayList<MediaAnalysisEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MediaAnalysisEntity _item;
            final long _tmpMediaId;
            _tmpMediaId = _cursor.getLong(_cursorIndexOfMediaId);
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final Long _tmpDHash;
            if (_cursor.isNull(_cursorIndexOfDHash)) {
              _tmpDHash = null;
            } else {
              _tmpDHash = _cursor.getLong(_cursorIndexOfDHash);
            }
            final Long _tmpPHash;
            if (_cursor.isNull(_cursorIndexOfPHash)) {
              _tmpPHash = null;
            } else {
              _tmpPHash = _cursor.getLong(_cursorIndexOfPHash);
            }
            final Double _tmpSharpnessVariance;
            if (_cursor.isNull(_cursorIndexOfSharpnessVariance)) {
              _tmpSharpnessVariance = null;
            } else {
              _tmpSharpnessVariance = _cursor.getDouble(_cursorIndexOfSharpnessVariance);
            }
            final Double _tmpMeanLuma;
            if (_cursor.isNull(_cursorIndexOfMeanLuma)) {
              _tmpMeanLuma = null;
            } else {
              _tmpMeanLuma = _cursor.getDouble(_cursorIndexOfMeanLuma);
            }
            final Boolean _tmpIsBlurry;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfIsBlurry)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfIsBlurry);
            }
            _tmpIsBlurry = _tmp == null ? null : _tmp != 0;
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpAnalyzedAtMillis;
            _tmpAnalyzedAtMillis = _cursor.getLong(_cursorIndexOfAnalyzedAtMillis);
            final String _tmpVideoCodec;
            if (_cursor.isNull(_cursorIndexOfVideoCodec)) {
              _tmpVideoCodec = null;
            } else {
              _tmpVideoCodec = _cursor.getString(_cursorIndexOfVideoCodec);
            }
            final Float _tmpFrameRate;
            if (_cursor.isNull(_cursorIndexOfFrameRate)) {
              _tmpFrameRate = null;
            } else {
              _tmpFrameRate = _cursor.getFloat(_cursorIndexOfFrameRate);
            }
            final Long _tmpBitrateBps;
            if (_cursor.isNull(_cursorIndexOfBitrateBps)) {
              _tmpBitrateBps = null;
            } else {
              _tmpBitrateBps = _cursor.getLong(_cursorIndexOfBitrateBps);
            }
            final Double _tmpBimodality;
            if (_cursor.isNull(_cursorIndexOfBimodality)) {
              _tmpBimodality = null;
            } else {
              _tmpBimodality = _cursor.getDouble(_cursorIndexOfBimodality);
            }
            _item = new MediaAnalysisEntity(_tmpMediaId,_tmpContentUri,_tmpDHash,_tmpPHash,_tmpSharpnessVariance,_tmpMeanLuma,_tmpIsBlurry,_tmpSizeBytes,_tmpAnalyzedAtMillis,_tmpVideoCodec,_tmpFrameRate,_tmpBitrateBps,_tmpBimodality);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object allByHash(final Continuation<? super List<MediaAnalysisEntity>> $completion) {
    final String _sql = "SELECT * FROM media_analysis ORDER BY pHash";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MediaAnalysisEntity>>() {
      @Override
      @NonNull
      public List<MediaAnalysisEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaId");
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDHash = CursorUtil.getColumnIndexOrThrow(_cursor, "dHash");
          final int _cursorIndexOfPHash = CursorUtil.getColumnIndexOrThrow(_cursor, "pHash");
          final int _cursorIndexOfSharpnessVariance = CursorUtil.getColumnIndexOrThrow(_cursor, "sharpnessVariance");
          final int _cursorIndexOfMeanLuma = CursorUtil.getColumnIndexOrThrow(_cursor, "meanLuma");
          final int _cursorIndexOfIsBlurry = CursorUtil.getColumnIndexOrThrow(_cursor, "isBlurry");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfAnalyzedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "analyzedAtMillis");
          final int _cursorIndexOfVideoCodec = CursorUtil.getColumnIndexOrThrow(_cursor, "videoCodec");
          final int _cursorIndexOfFrameRate = CursorUtil.getColumnIndexOrThrow(_cursor, "frameRate");
          final int _cursorIndexOfBitrateBps = CursorUtil.getColumnIndexOrThrow(_cursor, "bitrateBps");
          final int _cursorIndexOfBimodality = CursorUtil.getColumnIndexOrThrow(_cursor, "bimodality");
          final List<MediaAnalysisEntity> _result = new ArrayList<MediaAnalysisEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MediaAnalysisEntity _item;
            final long _tmpMediaId;
            _tmpMediaId = _cursor.getLong(_cursorIndexOfMediaId);
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final Long _tmpDHash;
            if (_cursor.isNull(_cursorIndexOfDHash)) {
              _tmpDHash = null;
            } else {
              _tmpDHash = _cursor.getLong(_cursorIndexOfDHash);
            }
            final Long _tmpPHash;
            if (_cursor.isNull(_cursorIndexOfPHash)) {
              _tmpPHash = null;
            } else {
              _tmpPHash = _cursor.getLong(_cursorIndexOfPHash);
            }
            final Double _tmpSharpnessVariance;
            if (_cursor.isNull(_cursorIndexOfSharpnessVariance)) {
              _tmpSharpnessVariance = null;
            } else {
              _tmpSharpnessVariance = _cursor.getDouble(_cursorIndexOfSharpnessVariance);
            }
            final Double _tmpMeanLuma;
            if (_cursor.isNull(_cursorIndexOfMeanLuma)) {
              _tmpMeanLuma = null;
            } else {
              _tmpMeanLuma = _cursor.getDouble(_cursorIndexOfMeanLuma);
            }
            final Boolean _tmpIsBlurry;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfIsBlurry)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfIsBlurry);
            }
            _tmpIsBlurry = _tmp == null ? null : _tmp != 0;
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpAnalyzedAtMillis;
            _tmpAnalyzedAtMillis = _cursor.getLong(_cursorIndexOfAnalyzedAtMillis);
            final String _tmpVideoCodec;
            if (_cursor.isNull(_cursorIndexOfVideoCodec)) {
              _tmpVideoCodec = null;
            } else {
              _tmpVideoCodec = _cursor.getString(_cursorIndexOfVideoCodec);
            }
            final Float _tmpFrameRate;
            if (_cursor.isNull(_cursorIndexOfFrameRate)) {
              _tmpFrameRate = null;
            } else {
              _tmpFrameRate = _cursor.getFloat(_cursorIndexOfFrameRate);
            }
            final Long _tmpBitrateBps;
            if (_cursor.isNull(_cursorIndexOfBitrateBps)) {
              _tmpBitrateBps = null;
            } else {
              _tmpBitrateBps = _cursor.getLong(_cursorIndexOfBitrateBps);
            }
            final Double _tmpBimodality;
            if (_cursor.isNull(_cursorIndexOfBimodality)) {
              _tmpBimodality = null;
            } else {
              _tmpBimodality = _cursor.getDouble(_cursorIndexOfBimodality);
            }
            _item = new MediaAnalysisEntity(_tmpMediaId,_tmpContentUri,_tmpDHash,_tmpPHash,_tmpSharpnessVariance,_tmpMeanLuma,_tmpIsBlurry,_tmpSizeBytes,_tmpAnalyzedAtMillis,_tmpVideoCodec,_tmpFrameRate,_tmpBitrateBps,_tmpBimodality);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final List<Long> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM media_analysis WHERE mediaId IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (long _item : ids) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
