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
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
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
public final class ExclusionDao_Impl implements ExclusionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ExclusionEntity> __insertionAdapterOfExclusionEntity;

  private final SharedSQLiteStatement __preparedStmtOfRemove;

  public ExclusionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfExclusionEntity = new EntityInsertionAdapter<ExclusionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `exclusions` (`id`,`type`,`uri`,`perceptualHash`,`folderPath`,`label`,`createdAtMillis`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExclusionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getType());
        if (entity.getUri() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getUri());
        }
        if (entity.getPerceptualHash() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPerceptualHash());
        }
        if (entity.getFolderPath() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getFolderPath());
        }
        statement.bindString(6, entity.getLabel());
        statement.bindLong(7, entity.getCreatedAtMillis());
      }
    };
    this.__preparedStmtOfRemove = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM exclusions WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object add(final ExclusionEntity exclusion, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfExclusionEntity.insert(exclusion);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object remove(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemove.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfRemove.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ExclusionEntity>> observeAll() {
    final String _sql = "SELECT * FROM exclusions ORDER BY createdAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"exclusions"}, new Callable<List<ExclusionEntity>>() {
      @Override
      @NonNull
      public List<ExclusionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfPerceptualHash = CursorUtil.getColumnIndexOrThrow(_cursor, "perceptualHash");
          final int _cursorIndexOfFolderPath = CursorUtil.getColumnIndexOrThrow(_cursor, "folderPath");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfCreatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMillis");
          final List<ExclusionEntity> _result = new ArrayList<ExclusionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExclusionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpUri;
            if (_cursor.isNull(_cursorIndexOfUri)) {
              _tmpUri = null;
            } else {
              _tmpUri = _cursor.getString(_cursorIndexOfUri);
            }
            final Long _tmpPerceptualHash;
            if (_cursor.isNull(_cursorIndexOfPerceptualHash)) {
              _tmpPerceptualHash = null;
            } else {
              _tmpPerceptualHash = _cursor.getLong(_cursorIndexOfPerceptualHash);
            }
            final String _tmpFolderPath;
            if (_cursor.isNull(_cursorIndexOfFolderPath)) {
              _tmpFolderPath = null;
            } else {
              _tmpFolderPath = _cursor.getString(_cursorIndexOfFolderPath);
            }
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final long _tmpCreatedAtMillis;
            _tmpCreatedAtMillis = _cursor.getLong(_cursorIndexOfCreatedAtMillis);
            _item = new ExclusionEntity(_tmpId,_tmpType,_tmpUri,_tmpPerceptualHash,_tmpFolderPath,_tmpLabel,_tmpCreatedAtMillis);
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
  public Object excludedHashes(final Continuation<? super List<Long>> $completion) {
    final String _sql = "SELECT perceptualHash FROM exclusions WHERE perceptualHash IS NOT NULL";
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
  public Object excludedFolders(final Continuation<? super List<String>> $completion) {
    final String _sql = "SELECT folderPath FROM exclusions WHERE folderPath IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
