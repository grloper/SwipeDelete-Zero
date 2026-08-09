package com.swipedelete.zero.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class StagedFileDao_Impl implements StagedFileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<StagedFileEntity> __insertionAdapterOfStagedFileEntity;

  private final SharedSQLiteStatement __preparedStmtOfUnstage;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  public StagedFileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfStagedFileEntity = new EntityInsertionAdapter<StagedFileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `staged_files` (`contentUri`,`displayName`,`mimeType`,`mediaType`,`sizeBytes`,`relativePath`,`stagedAtMillis`,`sourceDeckId`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final StagedFileEntity entity) {
        statement.bindString(1, entity.getContentUri());
        statement.bindString(2, entity.getDisplayName());
        statement.bindString(3, entity.getMimeType());
        statement.bindString(4, entity.getMediaType());
        statement.bindLong(5, entity.getSizeBytes());
        if (entity.getRelativePath() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getRelativePath());
        }
        statement.bindLong(7, entity.getStagedAtMillis());
        if (entity.getSourceDeckId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getSourceDeckId());
        }
      }
    };
    this.__preparedStmtOfUnstage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM staged_files WHERE contentUri = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM staged_files";
        return _query;
      }
    };
  }

  @Override
  public Object stage(final StagedFileEntity file, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfStagedFileEntity.insert(file);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object unstage(final String uri, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUnstage.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, uri);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUnstage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clear(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClear.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClear.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<StagedFileEntity>> observeAll() {
    final String _sql = "SELECT * FROM staged_files ORDER BY stagedAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"staged_files"}, new Callable<List<StagedFileEntity>>() {
      @Override
      @NonNull
      public List<StagedFileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relativePath");
          final int _cursorIndexOfStagedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "stagedAtMillis");
          final int _cursorIndexOfSourceDeckId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDeckId");
          final List<StagedFileEntity> _result = new ArrayList<StagedFileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StagedFileEntity _item;
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final String _tmpMediaType;
            _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpRelativePath;
            if (_cursor.isNull(_cursorIndexOfRelativePath)) {
              _tmpRelativePath = null;
            } else {
              _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            }
            final long _tmpStagedAtMillis;
            _tmpStagedAtMillis = _cursor.getLong(_cursorIndexOfStagedAtMillis);
            final String _tmpSourceDeckId;
            if (_cursor.isNull(_cursorIndexOfSourceDeckId)) {
              _tmpSourceDeckId = null;
            } else {
              _tmpSourceDeckId = _cursor.getString(_cursorIndexOfSourceDeckId);
            }
            _item = new StagedFileEntity(_tmpContentUri,_tmpDisplayName,_tmpMimeType,_tmpMediaType,_tmpSizeBytes,_tmpRelativePath,_tmpStagedAtMillis,_tmpSourceDeckId);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observeCount() {
    final String _sql = "SELECT COUNT(*) FROM staged_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"staged_files"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Long> observeStagedBytes() {
    final String _sql = "SELECT COALESCE(SUM(sizeBytes), 0) FROM staged_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"staged_files"}, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final long _tmp;
            _tmp = _cursor.getLong(0);
            _result = _tmp;
          } else {
            _result = 0L;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAll(final Continuation<? super List<StagedFileEntity>> $completion) {
    final String _sql = "SELECT * FROM staged_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<StagedFileEntity>>() {
      @Override
      @NonNull
      public List<StagedFileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfMediaType = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relativePath");
          final int _cursorIndexOfStagedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "stagedAtMillis");
          final int _cursorIndexOfSourceDeckId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDeckId");
          final List<StagedFileEntity> _result = new ArrayList<StagedFileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final StagedFileEntity _item;
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final String _tmpMediaType;
            _tmpMediaType = _cursor.getString(_cursorIndexOfMediaType);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpRelativePath;
            if (_cursor.isNull(_cursorIndexOfRelativePath)) {
              _tmpRelativePath = null;
            } else {
              _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            }
            final long _tmpStagedAtMillis;
            _tmpStagedAtMillis = _cursor.getLong(_cursorIndexOfStagedAtMillis);
            final String _tmpSourceDeckId;
            if (_cursor.isNull(_cursorIndexOfSourceDeckId)) {
              _tmpSourceDeckId = null;
            } else {
              _tmpSourceDeckId = _cursor.getString(_cursorIndexOfSourceDeckId);
            }
            _item = new StagedFileEntity(_tmpContentUri,_tmpDisplayName,_tmpMimeType,_tmpMediaType,_tmpSizeBytes,_tmpRelativePath,_tmpStagedAtMillis,_tmpSourceDeckId);
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
  public Object removeAll(final List<String> uris, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM staged_files WHERE contentUri IN (");
        final int _inputSize = uris.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : uris) {
          _stmt.bindString(_argIndex, _item);
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
